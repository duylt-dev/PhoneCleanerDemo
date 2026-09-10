package com.pion.phonecleaner.domain.repository

/**
 * Measures how sharp one photo is. The port the blurry-photo engine composes, exactly as the
 * similar-photo engine composes [PerceptualHasher] (`docs/system-architecture.md` §4.5).
 *
 * It is a port and not a function inside the scanner so that the scan can be tested with three
 * fixed numbers and no `Bitmap` — the same reason `PerceptualHasher` is one.
 */
interface BlurDetector {

    /**
     * The variance of the Laplacian response, or `null` when the photo could not be **measured**.
     *
     * `null` covers three cases and the caller must treat them alike: the file could not be decoded,
     * the URI was unreadable, or the photo is **too small to judge**. That third case is not an
     * error — see `LaplacianVariance.MIN_SCORABLE_EDGE`, which records why a small image must be
     * refused rather than enlarged to fit the working size.
     *
     * **The caller owns the dispatcher.** An implementation must not hop threads: the scan holds a
     * concurrency budget for the whole library, and a `withContext` here would step off it and
     * restore unbounded decoding.
     *
     * **Nullable, and never `0.0`.** Zero is the *blurriest possible* score, so answering it for a
     * photo that was never measured would pre-tick every one of them for deletion. The competitor
     * makes exactly this substitution in `od/b0.h`, which is why `Photo.perceptualHash` is nullable
     * too.
     *
     * The scale of the returned figure is fixed by the implementation's working image size and is
     * read against `com.pion.phonecleaner.domain.policy.BlurPolicy` — see that object's KDoc.
     */
    suspend fun score(uri: String): Double?
}
