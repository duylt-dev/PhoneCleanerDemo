package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.photo.BlurTier

/**
 * Where the line between sharp, soft and unusable is drawn.
 *
 * **This is the whole product decision of the blurry-photo screen, and it is one `when`.** It sits
 * in `:domain` and not beside the detector for the reason `PhotoGrouping` does: it is a rule with no
 * platform in it, a plain-JVM unit test reaches it with three numbers and no `Bitmap`, and the day a
 * threshold moves it moves in one file with the tests that pin it right there.
 *
 * ## The score these thresholds are read against
 *
 * The variance of the 3x3 Laplacian response over an 8-bit grey image — the standard sharpness
 * estimator. A sharp edge produces a large second derivative, so a well-focused photo has a wide
 * spread of responses; a blurred one has almost none and its variance collapses toward zero.
 *
 * **The numbers below are only meaningful because the detector normalises the image size first.**
 * Laplacian variance scales with resolution: the same photo measured at 4 000 px and at 500 px
 * gives two different figures, so a threshold against an un-normalised score is a threshold that
 * means something different for every camera. `LaplacianBlurDetector` scales every photo to a fixed
 * working edge before measuring, and that constant and these two are a matched set — **change one
 * and both thresholds must be re-derived.**
 *
 * ## Why the values are what they are
 *
 * [VERY_BLURRY_BELOW] `= 100.0` is the long-standing published working threshold for this estimator
 * on 8-bit grey. [BLURRY_BELOW] `= 300.0` is the softer bound this screen adds so that the rows
 * between the two can be drawn under their own header instead of being mixed into the confident
 * ones — see [BlurTier.SlightlyBlurry] for what actually lives in that band.
 *
 * They are deliberately **constants and not a percentile of the user's own library**. A relative cut
 * always finds blurry photos, including on a library where every photo is sharp, and "blurry" then
 * means "the worst you own" rather than "out of focus". The cost of a constant is the honest one:
 * on an unusually soft lens the screen finds more than it should, and on a very sharp one it finds
 * less.
 */
object BlurPolicy {

    /** Below this the photo is [BlurTier.VeryBlurry]. */
    const val VERY_BLURRY_BELOW: Double = 100.0

    /** Below this — and at or above [VERY_BLURRY_BELOW] — the photo is [BlurTier.SlightlyBlurry]. */
    const val BLURRY_BELOW: Double = 300.0

    /**
     * The tier [score] falls in, or `null` when the photo is sharp enough to leave alone.
     *
     * `null` rather than a `Sharp` constant: a sharp photo is not a row on this screen, so a tier
     * for it would exist only to be filtered back out. A **negative or NaN** score is also `null` —
     * it cannot be a variance, so it is a broken measurement, and a broken measurement must never
     * pre-tick a photo for deletion. This is the same refusal `Photo.perceptualHash` makes by being
     * nullable: the competitor's `b0.h` answers `0L` for a bitmap it could not read, which puts
     * every unreadable file on the device into one "similar" group.
     */
    fun tierOf(score: Double): BlurTier? = when {
        score.isNaN() || score < 0.0 -> null
        score < VERY_BLURRY_BELOW -> BlurTier.VeryBlurry
        score < BLURRY_BELOW -> BlurTier.SlightlyBlurry
        else -> null
    }
}
