package com.pion.phonecleaner.data.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one piece of arithmetic in the photo cluster with no reference implementation in the corpus.
 *
 * These are plain arrays, so the estimator is pinned without a `Bitmap`, an emulator or Robolectric.
 * That is the point: the defect this class's `MIN_SCORABLE_EDGE` now prevents shipped once precisely
 * because the maths was `private` inside the detector and no test could reach it.
 */
class LaplacianVarianceTest {

    /** A grey ramp with no edges anywhere: the second derivative is zero across the interior. */
    private fun flat(edge: Int, level: Int = 128) = IntArray(edge * edge) { level }

    /** Alternating black/white columns — the sharpest thing an 8-bit image can hold. */
    private fun stripes(edge: Int) = IntArray(edge * edge) { if ((it % edge) % 2 == 0) 0 else 255 }

    @Test
    fun `a perfectly flat image has no variance at all`() {
        assertEquals(0.0, LaplacianVariance.of(flat(16), 16, 16)!!, 1e-9)
    }

    @Test
    fun `a hard-edged pattern scores far above the sharp threshold`() {
        val score = LaplacianVariance.of(stripes(16), 16, 16)!!
        // Two orders of magnitude above BLURRY_BELOW = 300.0 — nothing about this is borderline.
        assertTrue("stripes scored $score", score > 100_000.0)
    }

    /**
     * The guarantee the whole estimator rests on: blur lowers the score, monotonically. Each pass is
     * a 3-tap box blur, so `sharp > onePass > twoPass`.
     */
    @Test
    fun `blurring the same content lowers the score every time`() {
        val edge = 32
        val sharp = stripes(edge)
        val once = boxBlur(sharp, edge)
        val twice = boxBlur(once, edge)

        val a = LaplacianVariance.of(sharp, edge, edge)!!
        val b = LaplacianVariance.of(once, edge, edge)!!
        val c = LaplacianVariance.of(twice, edge, edge)!!

        assertTrue("$a should exceed $b", a > b)
        assertTrue("$b should exceed $c", b > c)
    }

    /** The border ring is excluded, so a frame drawn only on the outermost pixels reads as flat. */
    @Test
    fun `the border ring is not measured`() {
        val edge = 8
        val bordered = flat(edge).also { pixels ->
            for (i in 0 until edge) {
                pixels[i] = 255
                pixels[(edge - 1) * edge + i] = 255
                pixels[i * edge] = 255
                pixels[i * edge + edge - 1] = 255
            }
        }
        // The interior touching that ring still responds, so this is not zero — but the ring's own
        // undefined response never enters the sum, which is what stops aspect ratio from biasing it.
        assertTrue(LaplacianVariance.of(bordered, edge, edge)!! > 0.0)
    }

    /**
     * `null`, never `0.0`. Zero is the blurriest possible score, so returning it for "there was
     * nothing to measure" would put an unmeasured photo at the top of a pre-ticked deletion list —
     * the competitor's `od/b0.h` defect with a worse ending.
     */
    @Test
    fun `an image with no interior is unmeasurable, not maximally blurry`() {
        assertNull(LaplacianVariance.of(IntArray(4), 2, 2))
        assertNull(LaplacianVariance.of(IntArray(0), 0, 0))
        assertNull(LaplacianVariance.of(IntArray(3), 1, 3))
    }

    @Test
    fun `an array too short for the stated dimensions is refused rather than read past its end`() {
        assertNull(LaplacianVariance.of(IntArray(10), 8, 8))
    }

    @Test
    fun `the minimum scorable edge is the working edge, so nothing is ever enlarged to be measured`() {
        // The C1 regression guard. Lowering MIN_SCORABLE_EDGE below WORK_EDGE re-admits upscaling,
        // and bilinear upscaling made a sharp 96px image score 2.3 — VeryBlurry, and on this screen
        // that means pre-ticked for deletion.
        assertTrue(LaplacianVariance.MIN_SCORABLE_EDGE >= LaplacianVariance.WORK_EDGE)
    }

    /** A 3-tap horizontal box blur; enough to make "blurrier" mean something in these fixtures. */
    private fun boxBlur(source: IntArray, edge: Int): IntArray {
        val out = IntArray(source.size)
        for (y in 0 until edge) {
            for (x in 0 until edge) {
                val left = source[y * edge + (x - 1).coerceAtLeast(0)]
                val here = source[y * edge + x]
                val right = source[y * edge + (x + 1).coerceAtMost(edge - 1)]
                out[y * edge + x] = (left + here + right) / 3
            }
        }
        return out
    }
}
