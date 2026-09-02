package com.pion.phonecleaner.feature.files.whatsapp

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WhatsAppBucket
import com.pion.phonecleaner.domain.model.file.WhatsAppBucketId
import com.pion.phonecleaner.domain.model.file.WhatsAppScanProgress
import com.pion.phonecleaner.domain.usecase.CleanFilesUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ScanWhatsAppUseCase
import com.pion.phonecleaner.feature.files.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.files.testing.FakeAppControlRepository
import com.pion.phonecleaner.feature.files.testing.FakeCleanupLedger
import com.pion.phonecleaner.feature.files.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.files.testing.FakeFileDeleter
import com.pion.phonecleaner.feature.files.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.files.testing.FakeWhatsAppScanner
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class WhatsAppCleanerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val scanner = FakeWhatsAppScanner()
    private val deleter = FakeFileDeleter()
    private val appControl = FakeAppControlRepository(mutableSetOf("com.whatsapp"))
    private val ledger = FakeCleanupLedger()
    private val analytics = FakeAnalyticsRepository()

    private fun viewModel() = WhatsAppCleanerViewModel(
        scanWhatsApp = ScanWhatsAppUseCase(scanner),
        cleanFiles = CleanFilesUseCase(deleter, ledger),
        appControl = appControl,
        permissions = FakePermissionRepository(),
        markFeatureUsed = MarkFeatureUsedUseCase(FakeFeatureUsageRepository()),
        analytics = analytics,
        log = AppLogger.NoOp,
    )

    /** Six zero-byte tiles is not an answer to "is WhatsApp even here?" (§6.5). */
    @Test
    fun `an uninstalled WhatsApp is a state, not six empty tiles`() = mainDispatcher.runVmTest {
        appControl.installed.clear()
        scanner.emissions = listOf(WhatsAppScanProgress.BucketFinished(bucket()))
        val vm = viewModel()

        vm.onIntent(WhatsAppCleanerIntent.ScreenStarted)
        settle()

        assertFalse(vm.state.value.whatsAppInstalled)
        assertTrue(vm.state.value.buckets.isEmpty())
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    /** Non-empty buckets are pre-selected in the reducer as each one lands (§6.2). */
    @Test
    fun `buckets arrive one at a time and the non-empty ones are pre-selected`() =
        mainDispatcher.runVmTest {
            scanner.emissions = listOf(
                WhatsAppScanProgress.BucketFinished(bucket()),
                WhatsAppScanProgress.BucketFinished(
                    WhatsAppBucket(WhatsAppBucketId.Image, persistentListOf()),
                ),
                WhatsAppScanProgress.Finished(),
            )
            val vm = viewModel()

            vm.onIntent(WhatsAppCleanerIntent.ScreenStarted)
            settle()

            assertEquals(2, vm.state.value.buckets.size)
            assertEquals(setOf(WhatsAppBucketId.Video), vm.state.value.selected.toSet())
            assertEquals(ToolPhase.Completing, vm.state.value.phase)
        }

    /** An empty scan shows an empty state. It does NOT press its own Clean button (§6.5). */
    @Test
    fun `an empty scan takes no action on the user's behalf`() = mainDispatcher.runVmTest {
        scanner.emissions = listOf(
            WhatsAppScanProgress.BucketFinished(
                WhatsAppBucket(WhatsAppBucketId.Video, persistentListOf()),
            ),
            WhatsAppScanProgress.Finished(),
        )
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(WhatsAppCleanerIntent.ScreenStarted)
            settle()
            vm.onIntent(WhatsAppCleanerIntent.CompletionAnimationFinished)
            settle(horizonMillis = 30_000L)

            expectNoEvents()
        }
        assertTrue(vm.state.value.isEmptyResult)
        assertTrue(deleter.requested.isEmpty())
    }

    private companion object {
        const val FileBytes = 2_048L

        fun bucket() = WhatsAppBucket(
            id = WhatsAppBucketId.Video,
            files = listOf(file("v1"), file("v2")).toImmutableList(),
        )

        fun file(id: String) = ScannedFile(
            id = id,
            path = "/media/$id.mp4",
            name = "$id.mp4",
            sizeBytes = FileBytes,
            kind = FileKind.Video,
            origin = FileOrigin.PlainFile,
        )
    }
}
