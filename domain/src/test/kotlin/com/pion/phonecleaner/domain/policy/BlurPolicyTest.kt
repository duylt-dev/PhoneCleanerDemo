package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.photo.BlurTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The whole product decision of the blurry-photo screen is one `when`, so it is pinned here with
 * numbers and no `Bitmap` — `LLM.md` §9: a rule that a plain-JVM test can reach is tested that way.
 */
class BlurPolicyTest {

    @Test
    fun `a score below the very-blurry threshold is the confident tier`() {
        assertEquals(BlurTier.VeryBlurry, BlurPolicy.tierOf(0.0))
        assertEquals(BlurTier.VeryBlurry, BlurPolicy.tierOf(99.999))
    }

    @Test
    fun `a score between the two thresholds is the tier that needs looking at`() {
        assertEquals(BlurTier.SlightlyBlurry, BlurPolicy.tierOf(BlurPolicy.VERY_BLURRY_BELOW))
        assertEquals(BlurTier.SlightlyBlurry, BlurPolicy.tierOf(299.999))
    }

    /** The bound is exclusive at the top: a photo exactly at the sharp threshold is left alone. */
    @Test
    fun `a score at or above the blurry threshold is not a row on this screen`() {
        assertNull(BlurPolicy.tierOf(BlurPolicy.BLURRY_BELOW))
        assertNull(BlurPolicy.tierOf(5_000.0))
    }

    /**
     * The one case that matters most on a screen that pre-ticks everything it finds: a broken
     * measurement must not become the blurriest possible reading and pre-select a photo for
     * deletion. This is the competitor's `od/b0.h` defect — `0L` for a bitmap it could not read —
     * refused at the policy level as well as at the port.
     */
    @Test
    fun `a negative or NaN score is a broken measurement, not a very blurry photo`() {
        assertNull(BlurPolicy.tierOf(-1.0))
        assertNull(BlurPolicy.tierOf(Double.NaN))
    }

    @Test
    fun `the two thresholds are ordered, so the slightly-blurry band is not empty`() {
        assert(BlurPolicy.VERY_BLURRY_BELOW < BlurPolicy.BLURRY_BELOW) {
            "VERY_BLURRY_BELOW must be the lower bound, or tierOf can never answer SlightlyBlurry"
        }
    }
}
