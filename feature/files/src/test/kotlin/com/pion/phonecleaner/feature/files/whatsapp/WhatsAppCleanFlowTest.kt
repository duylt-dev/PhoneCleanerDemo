package com.pion.phonecleaner.feature.files.whatsapp

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
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
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The clean half of `whatsapp`, split out so neither test file exceeds the size rule (`LLM.md` §4).
 * Every case here is about what the delete REPORTS: measured bytes, a failure that reaches the
 * state, and a cancel that keeps what is already gone.
 */
internal class WhatsAppCleanFlowTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val scanner = FakeWhatsAppScanner()
    private val deleter = FakeFileDeleter()
    private val appControl = FakeAppControlRepository(mutableSetOf("com.whatsapp"))
    private val ledger = FakeCleanupLedger()

    private fun viewModel() = WhatsAppCleanerViewModel(
        scanWhatsApp = ScanWhatsAppUseCase(scanner),
        cleanFiles = CleanFilesUseCase(deleter, ledger),
        appControl = appControl,
        permissions = FakePermissionRepository(),
        markFeatureUsed = MarkFeatureUsedUseCase(FakeFeatureUsageRepository()),
        analytics = FakeAnalyticsRepository(),
        log = AppLogger.NoOp,
    )

    /** Every sibling confirms before deleting; this one deletes chat media (§6.5). */
    @Test
    fun `the clean is confirmed first and the freed bytes are the measured ones`() =
        mainDispatcher.runVmTest {
            scanner.emissions = listOf(
                WhatsAppScanProgress.BucketFinished(bucket()),
                WhatsAppScanProgress.Finished(),
            )
            val vm = viewModel()
            vm.onIntent(WhatsAppCleanerIntent.ScreenStarted)
            settle()
            vm.onIntent(WhatsAppCleanerIntent.CompletionAnimationFinished)

            vm.effects.test {
                vm.onIntent(WhatsAppCleanerIntent.CleanPressed)
                assertNotNull(vm.state.value.confirm)
                assertTrue(deleter.requested.isEmpty())

                vm.onIntent(WhatsAppCleanerIntent.CleanConfirmed)
                settle()

                val effect = awaitItem()
                assertTrue(effect is WhatsAppCleanerEffect.NavigateToCleanResult)
                assertEquals(
                    2L * FileBytes,
                    (effect as WhatsAppCleanerEffect.NavigateToCleanResult).bytesFreed,
                )
            }
            assertNull(vm.state.value.cleaning)
            assertTrue(vm.state.value.buckets.single().isEmpty)
        }

    /** A failed delete reaches the state; the competitor hard-codes its outcome to `true` (§6.5). */
    @Test
    fun `a delete that removes nothing is reported, not swallowed`() = mainDispatcher.runVmTest {
        scanner.emissions = listOf(
            WhatsAppScanProgress.BucketFinished(bucket()),
            WhatsAppScanProgress.Finished(),
        )
        deleter.outcomes = mutableListOf(AppResult.Success(DeleteOutcome.NothingResolved))
        val vm = viewModel()
        vm.onIntent(WhatsAppCleanerIntent.ScreenStarted)
        settle()
        vm.onIntent(WhatsAppCleanerIntent.CompletionAnimationFinished)

        vm.effects.test {
            vm.onIntent(WhatsAppCleanerIntent.CleanPressed)
            vm.onIntent(WhatsAppCleanerIntent.CleanConfirmed)
            settle()

            val effect = awaitItem() as WhatsAppCleanerEffect.NavigateToCleanResult
            assertEquals(0L, effect.bytesFreed)
            assertEquals(0, effect.summary.itemCount)
        }
        assertEquals(0L, ledger.lifetime.value)
    }

    /**
     * Back during a clean asks first and does **not** leave; confirming the stop keeps what is
     * already gone and leaves the rest alone. The competitor has no cancel path at all.
     */
    @Test
    fun `back during a clean raises a stop confirm rather than leaving`() =
        mainDispatcher.runVmTest {
            scanner.emissions = listOf(
                WhatsAppScanProgress.BucketFinished(bucket()),
                WhatsAppScanProgress.Finished(),
            )
            deleter.gate = CompletableDeferred()
            val vm = viewModel()
            vm.onIntent(WhatsAppCleanerIntent.ScreenStarted)
            settle()
            vm.onIntent(WhatsAppCleanerIntent.CompletionAnimationFinished)

            vm.effects.test {
                vm.onIntent(WhatsAppCleanerIntent.CleanPressed)
                vm.onIntent(WhatsAppCleanerIntent.CleanConfirmed)
                settle()
                assertNotNull(vm.state.value.cleaning)

                vm.onIntent(WhatsAppCleanerIntent.BackPressed)
                assertNotNull(vm.state.value.stopConfirm)
                expectNoEvents()

                vm.onIntent(WhatsAppCleanerIntent.CancelCleanConfirmed)
                settle()
                assertNull(vm.state.value.cleaning)
                assertNull(vm.state.value.stopConfirm)
                assertEquals(ToolPhase.Ready, vm.state.value.phase)
                expectNoEvents()
            }
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
