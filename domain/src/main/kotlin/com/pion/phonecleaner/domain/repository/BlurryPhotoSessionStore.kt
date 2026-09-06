package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import kotlinx.collections.immutable.ImmutableList

/**
 * The scan hand-off between the blurry-photo grid and the full-screen pager.
 *
 * Same shape as [SimilarPhotoSessionStore], **two opposite rules**, and they are opposite for
 * reasons that are stated here rather than inherited by resemblance:
 *
 *  1. **[put] pre-selects every row, not every row but one.** A similar group is a set of rivals and
 *     keeping one of them is the whole point of that screen; a blur tier is not a set of rivals —
 *     nothing in it is the "best copy" of anything — so there is no member to hold back. This is the
 *     owner's decision of 2026-09-06, taken with the cost stated: a photo blurred on purpose (a
 *     bokeh portrait, a macro shot, a panned motion frame) scores as blurry and therefore arrives
 *     **pre-ticked for deletion**. The two mitigations that decision leaves in place are the tier
 *     split, so the riskier rows are visibly separated rather than mixed in, and the pager, so a row
 *     can be inspected at full size before the delete is confirmed.
 *  2. **[remove] keeps a tier that has fallen to one member.** `SimilarPhotoSessionStore.remove`
 *     drops a group below two because a group of one is not a similarity. One remaining blurry photo
 *     *is* still a blurry photo, and dropping it would make a row the user never acted on vanish
 *     from a screen that is still open.
 *
 * Being a separate `single` from the similar store is what makes those two rules independent: the
 * two screens can be open in the same back stack, and a delete on one must not prune the other's
 * groups.
 */
interface BlurryPhotoSessionStore : PhotoSessionStore {

    /** Stores a finished scan with **every** row pre-selected. See the interface KDoc, rule 1. */
    fun put(groups: ImmutableList<PhotoGroup>, skipped: Int = 0)
}
