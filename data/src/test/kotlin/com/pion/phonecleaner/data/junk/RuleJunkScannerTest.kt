package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.model.junk.AppRule
import com.pion.phonecleaner.domain.model.junk.AppRuleRoot
import com.pion.phonecleaner.domain.model.junk.JunkCategoryId
import com.pion.phonecleaner.domain.model.junk.JunkContentType
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.domain.model.junk.SystemCacheRule
import com.pion.phonecleaner.domain.repository.DirectorySizer
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.repository.JunkRuleCatalog
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * How the four passes are SEQUENCED, which is a separate question from what each one finds
 * ([JunkScanPassesTest]).
 *
 * The invariant worth a test is the emission contract, because a screen divides by it:
 * `JunkScanState.progress` is `passesFinished / 4`, so a pass that skipped its `PassFinished` when it
 * found nothing would leave the bar stuck at two thirds on a clean device. The competitor produces
 * the same information and throws it away — its only `listener.c(category, result)` implementation
 * has an empty body (`docs/screens/12-junk-cleaning.md` §1.2).
 */
internal class RuleJunkScannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `emits four PassFinished even when every pass finds nothing`() = runTest {
        val progress = scanner(root = temp.newFolder(), catalog = EmptyCatalog).scan().toList()

        assertEquals(4, progress.filterIsInstance<ScanProgress.PassFinished>().size)
        assertEquals(
            listOf(
                JunkCategoryId.SystemCache,
                JunkCategoryId.AppResidual,
                JunkCategoryId.ApkFiles,
                JunkCategoryId.TemporaryFiles,
            ),
            progress.filterIsInstance<ScanProgress.PassStarted>().map { it.category },
        )
        val finished = progress.last() as ScanProgress.Finished
        assertEquals(0L, finished.totalBytes)
        assertTrue(finished.categories.isEmpty())
    }

    @Test
    fun `totalBytes is the sum of the categories that produced items`() = runTest {
        val root = temp.newFolder()
        File(root, "cache").mkdirs()
        File(root, "Xender").mkdirs()

        val progress = scanner(root = root, catalog = TwoRuleCatalog).scan().toList()

        val finished = progress.last() as ScanProgress.Finished
        // 4 096 from the system-cache rule, 4 096 from the residual rule; Xender is not installed.
        assertEquals(8_192L, finished.totalBytes)
        assertEquals(2, finished.categories.size)
    }

    /**
     * The installed list is unreadable — a `PermissionDenied` from `InstalledAppsRepository`, which
     * is the arm that exists precisely so this is distinguishable from "no app is installed".
     * Treating it as an empty set would fire every residual rule at once against apps that are all
     * still on the device. The scan must still complete, with pass 2 contributing nothing.
     */
    @Test
    fun `an unreadable installed list silences pass two without failing the scan`() = runTest {
        val root = temp.newFolder()
        File(root, "cache").mkdirs()
        File(root, "Xender").mkdirs()

        val progress = scanner(root = root, catalog = TwoRuleCatalog, appsReadable = false).scan().toList()

        assertEquals(4, progress.filterIsInstance<ScanProgress.PassFinished>().size)
        val finished = progress.last() as ScanProgress.Finished
        assertEquals(listOf(JunkCategoryId.SystemCache), finished.categories.map { it.id })
    }

    private fun scanner(
        root: File,
        catalog: JunkRuleCatalog,
        appsReadable: Boolean = true,
    ) = RuleJunkScanner(
        catalog = catalog,
        roots = FakeRoots(root.absolutePath),
        sizer = FixedSizer(4_096L),
        scanner = EmptyScanner,
        installedApps = FakeInstalledApps(readable = appsReadable),
        dispatchers = TestDispatchers,
    )
}

private object EmptyCatalog : JunkRuleCatalog {
    override suspend fun systemCacheRules(): ImmutableList<SystemCacheRule> = persistentListOf()
    override suspend fun appRules(): ImmutableList<AppRule> = persistentListOf()
}

private object TwoRuleCatalog : JunkRuleCatalog {
    override suspend fun systemCacheRules(): ImmutableList<SystemCacheRule> = persistentListOf(
        SystemCacheRule("scratch-cache", "cache", JunkContentType.Cache),
    )

    override suspend fun appRules(): ImmutableList<AppRule> = persistentListOf(
        AppRule(
            packageName = "cn.xender",
            label = "Xender",
            aliases = persistentSetOf(),
            roots = persistentListOf(AppRuleRoot("Xender", scoped = false, subPaths = persistentListOf())),
        ),
    )
}

private class FakeRoots(private vararg val paths: String) : StorageRootProvider {
    override suspend fun readableRoots(): AppResult<ImmutableList<String>> =
        paths.toList().toImmutableList().asSuccess()

    override suspend fun coveredSurfaces(): AppResult<ImmutableList<String>> =
        persistentListOf<String>().asSuccess()
}

/** Reports [readable] = false as the failure arm, never as an empty list — the distinction under test. */
private class FakeInstalledApps(private val readable: Boolean) : InstalledAppsRepository {
    override suspend fun installedApps(
        includeWithoutLauncher: Boolean,
    ): AppResult<ImmutableList<InstalledApp>> =
        if (readable) persistentListOf<InstalledApp>().asSuccess() else AppError.PermissionDenied().asFailure()

    override suspend fun find(packageName: String): AppResult<InstalledApp?> = null.asSuccess()
}

private object EmptyScanner : StorageScanner {
    override fun walk(config: WalkConfig): Flow<ScannedFile> = emptyFlow()
}

/**
 * `Unconfined` on all three. The scanner's `flowOn(dispatchers.io)` is the line under test's nose —
 * a real IO dispatcher would move the walk off `runTest`'s scheduler and make the assertions race.
 */
private object TestDispatchers : DispatcherProvider {
    override val main = Dispatchers.Unconfined
    override val io = Dispatchers.Unconfined
    override val default = Dispatchers.Unconfined
}
