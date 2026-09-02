package com.pion.phonecleaner.feature.device

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The cluster's one percentage timeline (`docs/screens/18-device-battery-and-apps.md` §1.2, §6.4).
 *
 * Two scan screens draw the identical widget — `devicestatusscan` and `runningappsscan` — and the
 * competitor animates them with **two different interpolators**, `Decelerate` on one and `Linear` on
 * the other. §1.2 awards `Linear`, because there is now real work behind the beat, and puts the
 * interpolator in one place so the two cannot drift apart again.
 *
 * It is **arithmetic, not an animator**: a `ValueAnimator` lives on an Activity field, has to be
 * cancelled by hand in `onDestroy`, survives no config change, and would drag
 * `android.view.animation` into a ViewModel, which MVI §1 forbids outright.
 *
 * ### Where this file sits
 *
 * `LLM.md` §4 has a row for a composable shared by two screens of one cluster (`<cluster>/component/`)
 * and none for a **non-composable** shared by two of its ViewModels. The cluster root is the only
 * place that keeps it out of `component/` — which §3.7 defines as composables — without promoting a
 * suspend function nothing outside this module calls into `:core`. No new package is created.
 */
internal const val SCAN_PROGRESS_COMPLETE: Int = 100

/**
 * One frame at 60 Hz. The loop is **bounded** by construction: it runs a computed number of frames
 * and returns, so nothing can outlive the ViewModel even if a cancel is missed.
 */
internal val SCAN_FRAME_INTERVAL: Duration = 16.milliseconds

/**
 * The beat both percentage scans keep — the competitor's two merged `postDelayed` legs, 3 000 ms.
 *
 * It is a **floor, not a cost**: the real reading runs as a structural child concurrently with this,
 * so a slow device takes as long as its reading takes and a fast one still gets the beat.
 */
internal val SCAN_DURATION: Duration = 3.seconds

/**
 * Drives [onProgress] from `0` to [SCAN_PROGRESS_COMPLETE] over [duration], linearly.
 *
 * The caller runs it inside `launchSafely` and `join()`s it before finishing, which is what
 * guarantees the beat is never cut short by a reading that landed early.
 */
internal suspend fun tickScanProgress(
    duration: Duration = SCAN_DURATION,
    onProgress: (Int) -> Unit,
) {
    val frames = (duration / SCAN_FRAME_INTERVAL).toInt().coerceAtLeast(1)
    for (frame in 1..frames) {
        delay(SCAN_FRAME_INTERVAL)
        onProgress(frame * SCAN_PROGRESS_COMPLETE / frames)
    }
}
