package com.pion.phonecleaner.feature.files.whatsapp

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.FileToolEffect
import com.pion.phonecleaner.core.mvi.FileToolIntent
import com.pion.phonecleaner.core.mvi.FileToolState
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WhatsAppBucket
import com.pion.phonecleaner.domain.model.file.WhatsAppBucketId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `whatsapp` (`docs/screens/14-file-tools-and-app-manager.md` §6). Replaces `CorbafflActivity`
 * (506 L) and `Vacatur` (683 L) — the only tool that owns **buckets rather than files**, the only
 * one the competitor gives **no confirmation**, and the only one that **presses its own Clean
 * button** after a 4 s countdown when the scan finds nothing (§6.5).
 *
 * The path catalogue is not in this repository: [WhatsAppBucket]s arrive from `WhatsAppScanner`,
 * which joins the suffixes `WhatsAppRoots` holds to a root resolved by `StorageRootProvider`. No
 * screen here learns an absolute path, and none is written down (§6.2).
 */
@Immutable
data class CleanProgress(
    /** Captured when the clean starts and **never recomputed** — it is what was promised. */
    val promisedBytes: Long,

    /** What was ACTUALLY freed. The competitor posts this per file and discards the payload. */
    val deletedBytes: Long = 0L,
    val deletedCount: Int = 0,
    val failedCount: Int = 0,
    val totalCount: Int = 0,
    /**
     * True once `FileCleanProgress.Finished.recoverable` lands — the run moved into the bin rather
     * than freeing anything (plan `260908-0801-trash-bin`, Phase 07). Read by [cleanSummary].
     */
    val recoverable: Boolean = false,
) {
    val remainingBytes: Long get() = (promisedBytes - deletedBytes).coerceAtLeast(0L)
    val isFinished: Boolean get() = deletedCount + failedCount >= totalCount
}

@Immutable
data class WhatsAppCleanerState(
    override val phase: ToolPhase = ToolPhase.Idle,

    /** The competitor never asks, and answers an uninstalled WhatsApp with six zero tiles (§6.5). */
    val whatsAppInstalled: Boolean = true,
    val buckets: ImmutableList<WhatsAppBucket> = persistentListOf(),
    val selected: ImmutableSet<WhatsAppBucketId> = persistentSetOf(),

    /** The drill-down the competitor does not have; `null` = no sheet. */
    val expanded: WhatsAppBucketId? = null,

    /** The opt-in SAF tree for the legacy `/WhatsApp` root. Never all-files access (§0.2). */
    val legacyRootGranted: Boolean = false,

    /** Both dialogs are STATE, and they are never both up (`LLM.md` §7.4). */
    val confirm: ConfirmSpec? = null,
    /** Mode stated by the confirmation; retained through the system consent round trip. */
    val trashEligible: Boolean = false,
    val stopConfirm: ConfirmSpec? = null,
    val cleaning: CleanProgress? = null,
    override val error: AppError? = null,
) : FileToolState {

    val totalBytes: Long get() = buckets.sumOf { it.totalBytes }

    val selectedBytes: Long get() = buckets.sumOf { if (it.id in selected) it.totalBytes else 0L }

    val selectedFileCount: Int get() = buckets.sumOf { if (it.id in selected) it.fileCount else 0 }

    val canClean: Boolean
        get() = phase == ToolPhase.Ready && selectedBytes > 0L && cleaning == null

    val isEmptyResult: Boolean
        get() = phase == ToolPhase.Ready && whatsAppInstalled && totalBytes == 0L

    val expandedBucket: WhatsAppBucket?
        get() = expanded?.let { id -> buckets.firstOrNull { it.id == id } }
}

sealed interface WhatsAppCleanerIntent : UiIntent {
    data object ScreenStarted : WhatsAppCleanerIntent, FileToolIntent.Rescan
    data class BucketToggled(val id: WhatsAppBucketId) : WhatsAppCleanerIntent

    /** The shared `SelectionBar` owns this action; empty buckets are never selected by it. */
    data object AllBucketsToggled : WhatsAppCleanerIntent, FileToolIntent.ToggleSelectAll
    data class BucketExpanded(val id: WhatsAppBucketId?) : WhatsAppCleanerIntent
    data object GrantLegacyRootPressed : WhatsAppCleanerIntent
    data object CleanPressed : WhatsAppCleanerIntent, FileToolIntent.DeleteSelected
    data object CleanConfirmed : WhatsAppCleanerIntent
    data object CleanDismissed : WhatsAppCleanerIntent
    data object CompletionAnimationFinished : WhatsAppCleanerIntent
    data object BackPressed : WhatsAppCleanerIntent

    /** Stopping mid-clean keeps what is already gone. It is confirmed, not silent. */
    data object CancelCleanConfirmed : WhatsAppCleanerIntent
    data object CancelCleanDismissed : WhatsAppCleanerIntent

    /** The `MediaStore` delete-consent round trip — the default branch, not an error (§0.2). */
    data class DeleteConsentResult(val granted: Boolean) : WhatsAppCleanerIntent
}

sealed interface WhatsAppCleanerEffect : UiEffect {
    /** `ACTION_OPEN_DOCUMENT_TREE` for the legacy root. Never `MANAGE_EXTERNAL_STORAGE`. */
    data object RequestStorageTree : WhatsAppCleanerEffect
    data class RequestDeleteConsent(val token: PendingIntentToken) : WhatsAppCleanerEffect

    data class NavigateToCleanResult(val summary: CleanupSummary) :
        WhatsAppCleanerEffect, FileToolEffect.CleanFinished {
        override val bytesFreed: Long get() = summary.freedBytes
    }

    data object NavigateBack : WhatsAppCleanerEffect
}

/** The files a clean would touch, in bucket order. */
internal fun WhatsAppCleanerState.selectedFiles(): List<ScannedFile> =
    buckets.filter { it.id in selected }.flatMap { it.files }
