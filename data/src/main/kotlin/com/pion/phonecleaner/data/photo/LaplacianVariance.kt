package com.pion.phonecleaner.data.photo

/**
 * The sharpness arithmetic, with no `Bitmap` anywhere near it.
 *
 * Extracted for the reason `Dct` is extracted out of `DctPerceptualHasher`: this is the one piece of
 * numeric code in the photo cluster with **no reference implementation in the corpus** — the
 * competitor has no sharpness heuristic at all
 * (`docs/reverse-engineering/13-photo-and-media.md:544`) — so it is the piece that most needs a test
 * that can state an expected number. Left `private` inside the detector it was unreachable, and the
 * defect [MIN_SCORABLE_EDGE] now guards shipped because no test could see it.
 */
internal object LaplacianVariance {

    /**
     * The longest edge every photo is measured at.
     *
     * **`BlurPolicy.VERY_BLURRY_BELOW` and `BLURRY_BELOW` are calibrated against this number.**
     * Changing it silently re-scales every score; the thresholds must be re-derived with it.
     */
    const val WORK_EDGE = 512

    /**
     * A photo whose longest edge is below this is **not measurable** and is never given a score.
     *
     * ### Why this constant exists — the defect it prevents
     *
     * The obvious way to make scores comparable is to resample every photo to [WORK_EDGE], enlarging
     * the small ones. **That is wrong, and it is wrong in the most dangerous possible direction.**
     * Bilinear enlargement is a low-pass filter: it spreads a one-pixel edge over several pixels and
     * destroys exactly the high-frequency content the Laplacian measures. Measured on one sharp
     * image saved at several sizes, with the enlarging pipeline:
     *
     * | Source | Score | Verdict it produced |
     * |---|---|---|
     * | 2048 px | 30218 | sharp |
     * | 256 px | 3665 | sharp |
     * | 128 px | 288 | **SlightlyBlurry** |
     * | 96 px | 2.3 | **VeryBlurry** |
     *
     * For scale, the same 2048 px image blurred beyond recognition (Gaussian r=8) scores 161 — so a
     * *sharp* 96 px image read as seventy times blurrier than an unrecognisable one.
     *
     * `PhotoRepository.photos()` returns every image on the device with no dimension floor: avatars,
     * stickers, downloaded logos, messenger thumbnails, cached web images. On a screen where every
     * row arrives **pre-ticked for deletion** (the owner's decision of 2026-09-06), that is a library
     * of small files silently selected for removal. The owner accepted bokeh and macro false
     * positives; this was a different failure with a different cause and was not part of that trade.
     *
     * So the rule is: **measure by shrinking, never by enlarging.** A photo too small to shrink is
     * reported as unmeasurable — `BlurDetector.score` returns `null`, the scan counts it in
     * `skipped`, and it is never shown and never ticked.
     */
    const val MIN_SCORABLE_EDGE = WORK_EDGE

    /** Below 3 px on an edge there is no interior pixel with the four neighbours the kernel needs. */
    const val MIN_MEASURABLE_EDGE = 3

    /**
     * `E[x²] − E[x]²` of the 4-neighbour Laplacian `[[0,1,0],[1,-4,1],[0,1,0]]` over [grey].
     *
     * **The border ring is not measured.** A 3x3 kernel has no defined response on the outermost
     * pixels, and folding them in as zeroes would drag the variance down by an amount that depends
     * on the image's aspect ratio — i.e. would make wide photos read as blurrier than square ones.
     *
     * One pass, no intermediate array: a pixel's response is consumed as it is computed. Inputs are
     * grey levels in `0..255`, so a response is bounded by ±1020 and `count` by [WORK_EDGE]², and
     * every partial sum stays exact in a `Double`.
     *
     * Returns `null` — never `0.0` — when there is no interior to measure. Zero is the *blurriest
     * possible* score, so returning it for "I could not measure this" is the substitution that puts
     * an unmeasured photo at the top of a pre-ticked deletion list. That is the competitor's
     * `od/b0.h` defect (`0L` for a bitmap it could not read), and it is refused here, at
     * `BlurDetector.score`, and again at `BlurPolicy.tierOf`.
     */
    fun of(grey: IntArray, width: Int, height: Int): Double? {
        if (width < MIN_MEASURABLE_EDGE || height < MIN_MEASURABLE_EDGE) return null
        if (grey.size < width * height) return null
        var sum = 0.0
        var sumOfSquares = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val index = row + x
                val response = (
                    grey[index - width] + grey[index + width] +
                        grey[index - 1] + grey[index + 1] -
                        4 * grey[index]
                    ).toDouble()
                sum += response
                sumOfSquares += response * response
                count++
            }
        }
        if (count == 0) return null
        val mean = sum / count
        // Clamped at zero: the one-pass form can land a hair below it in floating point on a
        // perfectly flat image, and a negative score is not a variance.
        return ((sumOfSquares / count) - (mean * mean)).coerceAtLeast(0.0)
    }
}
