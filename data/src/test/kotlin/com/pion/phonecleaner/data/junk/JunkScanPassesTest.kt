package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.model.junk.AppRule
import com.pion.phonecleaner.domain.model.junk.AppRuleRoot
import com.pion.phonecleaner.domain.model.junk.JunkCategory
import com.pion.phonecleaner.domain.model.junk.JunkCategoryId
import com.pion.phonecleaner.domain.model.junk.JunkContentType
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.domain.model.junk.SystemCacheRule
import com.pion.phonecleaner.domain.repository.DirectorySizer
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The four passes, over a real directory tree rather than a mocked filesystem: every one of them
 * decides what to look at with `File.isDirectory`, so a test that stubbed that away would exercise
 * nothing the device runs.
 *
 * The pass under test here is pass 2. **Its rule is inverted** — a residual folder is junk only when
 * its app is GONE (`docs/reverse-engineering/12-junk-cleaning.md` §6.3 finding 2) — which means the
 * failure mode is not "we missed some junk" but "we offered a live app's data for deletion". The
 * three `appResidualPass` cases below are that one hazard, from three directions.
 */
internal class JunkScanPassesTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ---- pass 1 -------------------------------------------------------------------------------

    @Test
    fun `system cache pass sizes only the rule folders that exist`() = runTest {
        val root = temp.newFolder()
        File(root, "cache").mkdirs()

        val category = collect {
            systemCachePass(
                rules = listOf(
                    SystemCacheRule("scratch-cache", "cache", JunkContentType.Cache),
                    SystemCacheRule("thumbnails-dcim", "DCIM/.thumbnails", JunkContentType.Thumbnails),
                ),
                roots = listOf(root.absolutePath),
                sizer = FixedSizer(4_096L),
            )
        }

        assertEquals(JunkCategoryId.SystemCache, category?.id)
        assertEquals(1, category?.items?.size)
        // The label is the RULE fragment, not File(path).name — the whole point of Delta R4.
        assertEquals("cache", category?.items?.first()?.label)
    }

    // ---- pass 2, the inverted rule ------------------------------------------------------------

    @Test
    fun `app residual pass keeps a rule whose app is gone`() = runTest {
        val root = temp.newFolder()
        File(root, "Xender").mkdirs()

        val category = collect {
            appResidualPass(
                rules = listOf(rule("cn.xender", "Xender")),
                roots = listOf(root.absolutePath),
                sizer = FixedSizer(2_048L),
                installedPackages = setOf("com.pion.phonecleaner"),
            )
        }

        assertEquals(JunkCategoryId.AppResidual, category?.id)
        assertEquals(2_048L, category?.totalBytes)
    }

    @Test
    fun `app residual pass drops a rule whose app is still installed`() = runTest {
        val root = temp.newFolder()
        File(root, "Xender").mkdirs()

        val category = collect {
            appResidualPass(
                rules = listOf(rule("cn.xender", "Xender")),
                roots = listOf(root.absolutePath),
                sizer = FixedSizer(2_048L),
                installedPackages = setOf("cn.xender"),
            )
        }

        assertNull("a live app's folder must never become a candidate", category)
    }

    /**
     * The alias half. Telegram's web and beta channels write the same `/Telegram` directory, so a
     * rule matched on its primary package alone reports the app as uninstalled and offers a live
     * install's downloads for deletion. The competitor stores this field at fifteen call sites and
     * has no getter for it.
     */
    @Test
    fun `app residual pass drops a rule whose alias is installed`() = runTest {
        val root = temp.newFolder()
        File(root, "Telegram").mkdirs()

        val category = collect {
            appResidualPass(
                rules = listOf(
                    rule("org.telegram.messenger", "Telegram", aliases = setOf("org.telegram.messenger.web")),
                ),
                roots = listOf(root.absolutePath),
                sizer = FixedSizer(2_048L),
                installedPackages = setOf("org.telegram.messenger.web"),
            )
        }

        assertNull(category)
    }

    /**
     * "The installed list could not be read" is a third state, and treating it as "nothing is
     * installed" would invert every rule at once. The pass must still START and FINISH, or the
     * progress bar loses a third of itself.
     */
    @Test
    fun `app residual pass yields nothing when the installed list is unknown`() = runTest {
        val root = temp.newFolder()
        File(root, "Xender").mkdirs()

        val emissions = emissionsOf {
            appResidualPass(
                rules = listOf(rule("cn.xender", "Xender")),
                roots = listOf(root.absolutePath),
                sizer = FixedSizer(2_048L),
                installedPackages = null,
            )
        }

        assertEquals(1, emissions.size)
        assertEquals(ScanProgress.PassStarted(JunkCategoryId.AppResidual), emissions.first())
    }

    @Test
    fun `app residual pass skips a scoped root`() = runTest {
        val root = temp.newFolder()
        File(root, "Android/data/cn.xender").mkdirs()

        val category = collect {
            appResidualPass(
                rules = listOf(
                    AppRule(
                        packageName = "cn.xender",
                        label = "Xender",
                        aliases = persistentSetOf(),
                        roots = persistentListOf(
                            AppRuleRoot("Android/data/cn.xender", scoped = true, subPaths = persistentListOf()),
                        ),
                    ),
                ),
                roots = listOf(root.absolutePath),
                sizer = FixedSizer(2_048L),
                installedPackages = emptySet(),
            )
        }

        assertNull("Android/data is unreadable on API 30+, so it is skipped, not reported", category)
    }

    // ---- pass 3 -------------------------------------------------------------------------------

    @Test
    fun `apk pass keeps only apk files`() = runTest {
        val category = collect {
            apkPass(
                roots = listOf("/irrelevant"),
                scanner = FakeScanner(
                    listOf(
                        scannedFile("/irrelevant/old.apk", FileKind.Apk, 1_000L),
                        scannedFile("/irrelevant/photo.jpg", FileKind.Image, 9_000L),
                    ),
                ),
            )
        }

        assertEquals(1, category?.items?.size)
        assertEquals(1_000L, category?.totalBytes)
        // Indeterminate: the pass discovers its own candidates, so it may not invent a total.
        val totals = emissionsOf {
            apkPass(
                roots = listOf("/irrelevant"),
                scanner = FakeScanner(listOf(scannedFile("/irrelevant/old.apk", FileKind.Apk, 1L))),
            )
        }.filterIsInstance<ScanProgress.Candidate>().map { it.total }
        assertTrue(totals.all { it == JunkWalkBounds.TOTAL_UNKNOWN })
    }

    @Test
    fun `temporary files pass keeps tmp and log files`() = runTest {
        val category = collect {
            temporaryFilesPass(
                roots = listOf("/irrelevant"),
                scanner = FakeScanner(
                    listOf(
                        scannedFile("/irrelevant/session.tmp", FileKind.Other, 100L),
                        scannedFile("/irrelevant/crash.LOG", FileKind.Other, 200L),
                        scannedFile("/irrelevant/photo.jpg", FileKind.Image, 9_000L),
                    ),
                ),
            )
        }

        assertEquals(JunkCategoryId.TemporaryFiles, category?.id)
        assertEquals(listOf("session.tmp", "crash.LOG"), category?.items?.map { it.label })
        assertEquals(300L, category?.totalBytes)

        val totals = emissionsOf {
            temporaryFilesPass(
                roots = listOf("/irrelevant"),
                scanner = FakeScanner(listOf(scannedFile("/irrelevant/session.tmp", FileKind.Other, 1L))),
            )
        }.filterIsInstance<ScanProgress.Candidate>().map { it.total }
        assertTrue(totals.all { it == JunkWalkBounds.TOTAL_UNKNOWN })
    }
}

// ---- helpers ----------------------------------------------------------------------------------

/** Runs a pass to completion and returns the category it produced, discarding the progress stream. */
private suspend fun collect(
    pass: suspend FlowCollector<ScanProgress>.() -> JunkCategory?,
): JunkCategory? {
    var result: JunkCategory? = null
    flow { result = pass() }.toList()
    return result
}

/** The other half: the progress stream, for the assertions that are about what a pass EMITS. */
private suspend fun emissionsOf(
    pass: suspend FlowCollector<ScanProgress>.() -> JunkCategory?,
): List<ScanProgress> = flow { pass() }.toList()

private fun rule(
    packageName: String,
    directory: String,
    aliases: Set<String> = emptySet(),
): AppRule = AppRule(
    packageName = packageName,
    label = directory,
    aliases = aliases.toImmutableSet(),
    roots = persistentListOf(AppRuleRoot(directory, scoped = false, subPaths = persistentListOf())),
)

private fun scannedFile(path: String, kind: FileKind, size: Long) = ScannedFile(
    id = path,
    path = path,
    name = path.substringAfterLast('/'),
    sizeBytes = size,
    kind = kind,
    origin = FileOrigin.PlainFile,
    mimeType = null,
    lastModifiedAtMillis = 0L,
)

/**
 * Every directory it is asked about is [bytes] big, so a test asserts on selection, not on sizing.
 * `internal` rather than `private`: [RuleJunkScannerTest] needs the same fake and a second copy would
 * be the one that drifts.
 */
internal class FixedSizer(private val bytes: Long) : DirectorySizer {
    override suspend fun sizeOf(config: WalkConfig): AppResult<Long> =
        if (bytes >= 0) bytes.asSuccess() else AppError.Storage().asFailure()
}

private class FakeScanner(private val files: List<ScannedFile>) : StorageScanner {
    override fun walk(config: WalkConfig): Flow<ScannedFile> = files.asFlow()
}
