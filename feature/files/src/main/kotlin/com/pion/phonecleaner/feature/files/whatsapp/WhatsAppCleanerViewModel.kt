package com.pion.phonecleaner.feature.files.whatsapp

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.FileCleanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WhatsAppScanProgress
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.AppControlRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.CleanFilesUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ScanWhatsAppUseCase
import kotlinx.coroutines.Job

/**
 * `whatsapp` (`docs/screens/14-file-tools-and-app-manager.md` §6.2).
 *
 * **No pacing.** The competitor delays `4000 / fileCount` ms per file and then adds a 4 s floor
 * after the delete — about 8 s on screen, both filling an ad window. It also arms a 4 s countdown
 * on an empty scan and then **presses its own Clean button**; an empty scan here shows an empty
 * state and takes no action on the user's behalf (§6.5).
 *
 * The clean goes through `CleanFilesUseCase` — the same use case the junk cluster uses. There is no
 * second engine and no second recursive walker.
 */
class WhatsAppCleanerViewModel(
    private val scanWhatsApp: ScanWhatsAppUseCase,
    private val cleanFiles: CleanFilesUseCase,
    private val appControl: AppControlRepository,
    private val permissions: PermissionRepository,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<WhatsAppCleanerState, WhatsAppCleanerIntent, WhatsAppCleanerEffect>(
    WhatsAppCleanerState(),
    log,
) {

    private var scanJob: Job? = null
    private var cleanJob: Job? = null

    /** What a consent round trip must re-issue, captured before the request goes out. */
    private var pendingFiles: List<ScannedFile> = emptyList()

    override fun onIntent(intent: WhatsAppCleanerIntent) {
        when (intent) {
            WhatsAppCleanerIntent.ScreenStarted -> onScreenStarted()
            is WhatsAppCleanerIntent.BucketToggled -> setState { withBucketToggled(intent.id) }
            WhatsAppCleanerIntent.AllBucketsToggled -> setState { withAllBucketsToggled() }
            is WhatsAppCleanerIntent.BucketExpanded -> setState { copy(expanded = intent.id) }
            WhatsAppCleanerIntent.GrantLegacyRootPressed ->
                sendEffect(WhatsAppCleanerEffect.RequestStorageTree)

            WhatsAppCleanerIntent.CleanPressed -> onCleanPressed()
            WhatsAppCleanerIntent.CleanConfirmed -> runClean(currentState.selectedFiles())
            WhatsAppCleanerIntent.CleanDismissed -> setState { copy(confirm = null) }
            WhatsAppCleanerIntent.CompletionAnimationFinished ->
                setState { copy(phase = ToolPhase.Ready) }

            WhatsAppCleanerIntent.CancelCleanConfirmed -> onCancelClean()
            WhatsAppCleanerIntent.CancelCleanDismissed -> setState { copy(stopConfirm = null) }
            is WhatsAppCleanerIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            WhatsAppCleanerIntent.BackPressed -> onBackPressed()
        }
    }

    /** Idempotent: it arrives on every `ON_START`, including the return from the tree picker. */
    private fun onScreenStarted() {
        launchSafely { markFeatureUsed(FeatureId.WhatsAppCleaner) }
        setState { copy(legacyRootGranted = permissions.isGranted(AppPermission.WhatsAppFolder)) }
        if (currentState.phase == ToolPhase.Scanning || currentState.cleaning != null) return
        startScan()
    }

    private fun startScan() {
        scanJob?.cancel()
        setState { copy(phase = ToolPhase.Scanning, error = null) }
        scanJob = launchSafely(onError = ::onFailure) {
            // Asked once, before the walk: six zero-byte tiles is not an answer to "is it here?".
            val installed = appControl.isInstalled(WHATSAPP_PACKAGE)
            setState { copy(whatsAppInstalled = installed) }
            if (!installed) {
                setState { copy(phase = ToolPhase.Ready) }
                return@launchSafely
            }
            scanWhatsApp().collect(::reduceScan)
        }
    }

    private fun reduceScan(progress: WhatsAppScanProgress) = when (progress) {
        is WhatsAppScanProgress.BucketFinished -> setState { withBucketFinished(progress.bucket) }
        is WhatsAppScanProgress.Finished -> setState { copy(phase = ToolPhase.Completing) }
    }

    private fun onCleanPressed() {
        if (currentState.canClean) {
            setState { copy(confirm = cleanConfirmSpec(selectedFileCount)) }
        }
    }

    private fun runClean(files: List<ScannedFile>) {
        if (files.isEmpty()) {
            setState { copy(confirm = null) }
            return
        }
        pendingFiles = files
        analytics.track(AnalyticsEvent.CleanRequested(files.sumOf { it.sizeBytes }, files.size))
        setState {
            copy(
                confirm = null,
                phase = ToolPhase.Deleting,
                cleaning = CleanProgress(
                    promisedBytes = files.sumOf { it.sizeBytes },
                    totalCount = files.size,
                ),
            )
        }
        cleanJob = launchSafely(onError = ::onFailure) {
            cleanFiles(files).collect(::reduceClean)
        }
    }

    private fun reduceClean(progress: FileCleanProgress) {
        setState { withCleanProgress(progress) }
        when (progress) {
            is FileCleanProgress.Deleted -> setState { withDeleted(progress.ids.toSet()) }

            // The normal path in the default storage branch, and NOT an error (§0.2).
            is FileCleanProgress.NeedsConsent -> {
                pendingFiles = pendingFiles.filter { it.id in progress.ids }
                sendEffect(WhatsAppCleanerEffect.RequestDeleteConsent(progress.request))
            }

            is FileCleanProgress.Finished -> finishClean()
            is FileCleanProgress.Failed -> Unit
        }
    }

    private fun finishClean() {
        pendingFiles = emptyList()
        val summary = currentState.cleanSummary()
        setState { copy(phase = ToolPhase.Ready, cleaning = null, stopConfirm = null) }
        sendEffect(WhatsAppCleanerEffect.NavigateToCleanResult(summary))
    }

    private fun onConsentResult(granted: Boolean) {
        val files = pendingFiles
        pendingFiles = emptyList()
        if (granted && files.isNotEmpty()) runClean(files) else finishClean()
    }

    /** What is already removed stays removed; the rest is left alone. Nothing is retried. */
    private fun onCancelClean() {
        cleanJob?.cancel()
        setState { copy(phase = ToolPhase.Ready, cleaning = null, stopConfirm = null) }
        pendingFiles = emptyList()
    }

    /** Back during a clean asks first — it is the one moment the answer is not free. */
    private fun onBackPressed() {
        val cleaning = currentState.cleaning
        if (cleaning != null) {
            setState { copy(stopConfirm = stopConfirmSpec(cleaning.deletedCount)) }
            return
        }
        scanJob?.cancel()
        sendEffect(WhatsAppCleanerEffect.NavigateBack)
    }

    private fun onFailure(error: AppError) = setState {
        copy(phase = ToolPhase.Ready, confirm = null, stopConfirm = null, error = error)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        cleanJob?.cancel()
    }

    private companion object {
        /** §6.2: the installed check the competitor never makes. */
        const val WHATSAPP_PACKAGE = "com.whatsapp"
    }
}
