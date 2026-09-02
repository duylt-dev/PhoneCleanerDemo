package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList

/**
 * What a delete actually did. Every arm has to be handled by every delete screen — including in the
 * default (MediaStore + SAF) branch, where `PendingConsent` is the normal path and in the all-files
 * branch simply never fires (`docs/system-architecture.md` §8.4). A screen that omits an arm is what
 * makes the storage branch expensive to switch.
 */
sealed interface DeleteOutcome {

    data class Deleted(
        /** The [ScannedFile.id]s that are gone. */
        val ids: ImmutableList<String>,
        val freedBytes: Long,
        /** Paths the delete could not remove. Reported, never swallowed. */
        val failedPaths: ImmutableList<String>,
    ) : DeleteOutcome

    /**
     * `MediaStore.createDeleteRequest` — only an Activity can launch the `IntentSender`, so the
     * ViewModel raises this as an Effect and the Route unwraps it.
     */
    data class PendingConsent(
        val request: PendingIntentToken,
        val ids: ImmutableList<String>,
    ) : DeleteOutcome

    /**
     * Nothing the request named could be resolved — a first-class outcome, not silence. The
     * competitor's video delete returns without invoking its callback when nothing resolves,
     * stranding its busy flag forever (`docs/screens/14-file-tools-and-app-manager.md:456`).
     */
    data object NothingResolved : DeleteOutcome
}

/**
 * The one deliberate platform leak into a `:domain` signature (`LLM.md` §12).
 *
 * It is acceptable because **the ViewModel never calls a method on it** — it carries the value from
 * the repository to the Route, which unwraps it to launch the `IntentSender`. Same shape as the MVI
 * doc's "platform state lives in the composable and reports upward".
 */
@JvmInline
value class PendingIntentToken(val value: Any)
