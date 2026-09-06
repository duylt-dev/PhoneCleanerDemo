package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.PhotoSession
import kotlinx.coroutines.flow.StateFlow

/**
 * The scan hand-off a photo grid shares with the full-screen pager
 * (`docs/screens/13-photo-and-media.md` §2.2, §2.4).
 *
 * **Why a supertype exists at all.** Two grids now hand a scan to the same pager —
 * [SimilarPhotoSessionStore] and [BlurryPhotoSessionStore] — and the pager needs exactly these
 * three members plus the feed. Giving `PhotoPreviewViewModel` this type instead of one of the two
 * concrete stores is what keeps it free of a `when` over the feature that produced the session: it
 * is handed the store its route argument names and cannot tell them apart. Copying the pager was
 * the alternative, and it is a real screen with a pager, a selection toggle and a session-lost
 * branch — duplicating all of it to vary one policy is not the trade `LLM.md` §12 defends, which
 * pays for a genuine difference in *fields* and mandates a shared builder even then.
 *
 * **`put` is deliberately NOT on this interface.** It is the one member whose contract differs per
 * feature — it is where pre-selection is decided — so each store declares its own and documents the
 * rule it applies. A caller that could `put` through this type would be a caller that does not know
 * which rule it just invoked.
 */
interface PhotoSessionStore {

    /** `null` means no scan has been stored, or the process died since it was. */
    val session: StateFlow<PhotoSession?>

    /** Replaces the selection. Both screens observe the result; neither owns it. */
    fun select(ids: Set<PhotoId>)

    /** Drops rows that are gone, and the selection entries that pointed at them. */
    fun remove(ids: Set<PhotoId>)

    fun clear()
}
