package com.pion.phonecleaner.domain.model.cleanup

import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlinx.serialization.Serializable

/**
 * The argument of the one shared clean-result route. Four scalars, so type-safe navigation can carry
 * it (`docs/system-architecture.md` §4.6).
 *
 * `:feature:cleanresult` is one parameterised route replacing two competitor screens and serving
 * fifteen features. What that one move deletes: a destination whose Back key must be blocked with a
 * toast, a `ValueAnimator` `onDestroy` fails to cancel, a `Float.parseFloat("null")` crash path, a
 * second copy of the `fileSize`/`fileUnit` extras, and two disagreeing `when (tag)` blocks whose id
 * sets have already drifted — `tag 8` is handled although no feature has id 8.
 *
 * [freedBytes] is a raw `Long` the whole way. `ByteFormatter` is display-only and **`parseBytes` does
 * not exist** (`docs/system-architecture.md` §4.2): the competitor's `md.g4.e()` parses formatted
 * output back into a `Long`, loses about 5 % to `DecimalFormat("###.0")`, and defaults an
 * unrecognised unit to MB.
 */
@Serializable
data class CleanupSummary(
    val feature: FeatureId,
    /** BYTES. Not "1.2" plus "GB". */
    val freedBytes: Long = 0L,
    /** The notification cleaner counts notifications, not bytes. */
    val itemCount: Int = 0,
    val outcome: CleanupOutcome,
)

/**
 * `DataCleared` is why the shared result screen can render a run that freed nothing and still be
 * correct — the photo-privacy strip clears location data and frees zero bytes
 * (`docs/screens/13-photo-and-media.md:633`).
 *
 * `MovedToTrash` is the arm that keeps this screen honest once the bin exists (plan
 * `260908-0801-trash-bin`, Phase 07). The bytes in [CleanupSummary.freedBytes] have **not** left the
 * device — they are in the app's bin for two days — so the headline says so and `CleanupLedger` is
 * not written. Crediting them here would make the lifetime figure count the same bytes twice: once
 * on the move and once on the purge.
 */
@Serializable
enum class CleanupOutcome {
    Cleaned,
    NothingFound,
    ThreatsRemoved,
    DataCleared,
    ItemsCleared,
    MovedToTrash,
}
