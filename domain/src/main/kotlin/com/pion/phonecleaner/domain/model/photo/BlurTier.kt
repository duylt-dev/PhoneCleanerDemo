package com.pion.phonecleaner.domain.model.photo

/**
 * How blurred a photo is, as the two buckets the grid draws under their own headers.
 *
 * **Two tiers and not one number.** The screen never shows the raw sharpness figure: it is a
 * variance in grey levels, it means nothing to a reader, and `LLM.md` §1 bans presenting a computed
 * figure as a quality claim. What the reader can act on is "this one is almost certainly a mistake"
 * versus "look at this one before you delete it", which is exactly two buckets.
 *
 * **Two tiers and not three.** A `Sharp` constant would be a value no row on this screen can hold —
 * a sharp photo is not in the result at all — so it would exist only to be filtered out. The
 * absence of a tier *is* "sharp", and [BlurPolicy.tierOf] returns `null` to say so.
 *
 * The order of the constants is the order the sections are drawn in: the rows most likely to be
 * unwanted come first, because they are the ones a reader who scrolls no further should see.
 */
enum class BlurTier {

    /**
     * Far below the sharpness floor — a camera shake, a finger over the lens, a frame grabbed while
     * the autofocus was still hunting.
     */
    VeryBlurry,

    /**
     * Between the two thresholds. Soft, and *sometimes soft on purpose*: this is the tier a bokeh
     * portrait, a macro shot or a deliberate motion pan lands in. It is drawn second and under its
     * own header for that reason — the rows are still pre-selected (the owner's decision), and the
     * header is what tells the reader these are the ones worth opening first.
     */
    SlightlyBlurry,
}
