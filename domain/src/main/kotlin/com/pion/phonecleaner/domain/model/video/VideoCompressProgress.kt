package com.pion.phonecleaner.domain.model.video

/**
 * The element type of [com.pion.phonecleaner.domain.repository.VideoCompressor.compress]'s flow
 * (`phase-03-domain-video-compression.md` step 3b).
 *
 * **Why this is not simply `Flow<VideoCompressStep>`, the way `PhotoCompressor.compress` is.** A
 * photo re-encode takes under a second, so a per-photo step *is* the progress. A video takes
 * minutes, so a bar that only moves once per video does not move at all. The flow therefore carries
 * intra-video progress as its own arm, and the run screen folds only [Finished] into its counts — a
 * [Working] can never be mistaken for a completed item.
 *
 * [Working.percent] is emitted **only** when `Transformer.getProgress` returns
 * `PROGRESS_STATE_AVAILABLE` (Phase 04). `WAITING_FOR_AVAILABILITY` and `UNAVAILABLE` emit no
 * [Working] at all, so the screen shows an indeterminate state rather than a number the engine did
 * not produce — the wording rule applied to a progress bar. Same idiom as `BlurScanProgress` and
 * `DuplicateScanProgress`.
 */
sealed interface VideoCompressProgress {
    /** The engine is working on [id]. [percent] is 0..100 and comes from the engine itself. */
    data class Working(val index: Int, val total: Int, val id: String, val percent: Int) :
        VideoCompressProgress

    /** One video is done, one way or another. */
    data class Finished(val step: VideoCompressStep) : VideoCompressProgress
}
