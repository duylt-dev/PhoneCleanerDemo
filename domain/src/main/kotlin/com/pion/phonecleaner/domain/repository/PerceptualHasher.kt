package com.pion.phonecleaner.domain.repository

/**
 * The DCT perceptual hash of one image, and the distance between two of them
 * (`docs/screens/13-photo-and-media.md` §0.3).
 *
 * A port rather than a utility because the implementation decodes bitmaps, which is a platform
 * capability `:domain` may not name (`LLM.md` §2), and because the grouping engine must be unit
 * testable against fixture hashes without a device (§1.4).
 */
interface PerceptualHasher {

    /**
     * `null` when the image could not be decoded.
     *
     * `od/b0.h` returns `0L` in that case, so every undecodable file in the competitor collapses into
     * a single "similar" group, their mutual distance being zero
     * (`docs/reverse-engineering/13-photo-and-media.md` §4.1 step 2).
     */
    suspend fun hash(uri: String): Long?

    /** Hamming distance, 0..64. */
    fun distance(a: Long, b: Long): Int
}
