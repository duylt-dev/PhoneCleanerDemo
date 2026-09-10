package com.pion.phonecleaner.feature.trash

import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.policy.TrashRetention
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlin.time.Instant

/**
 * The pure half of `trash`: state in, state out, no coroutine and no repository. Split out of the
 * ViewModel so neither file exceeds the size rule (`LLM.md` §4) and so these are testable without
 * constructing a ViewModel at all (`docs/android-mvi-best-practices.md` §7).
 */
internal fun TrashState.withToggled(id: String): TrashState = if (!canSelect || entries.none { it.id == id }) this else copy(
    selectedIds = if (id in selectedIds) (selectedIds - id).toImmutableSet() else (selectedIds + id).toImmutableSet(),
)

internal fun TrashState.withAllToggled(): TrashState = if (!canSelect) this else copy(
    selectedIds = if (isAllSelected) persistentSetOf() else entries.map { it.id }.toImmutableSet(),
)

internal fun TrashState.withoutConfirmation(): TrashState = copy(
    confirm = null, pendingAction = null, pendingIds = persistentSetOf(),
)

/**
 * The only `destructive = false` confirm on this screen: a restore puts files back, it removes
 * nothing. It is asked anyway because the outcome is not fully predictable from the button — a
 * restore can rename, when a file of that name has appeared at the original location since
 * (`TrashMover.moveOut` never overwrites), and a bulk restore of 300 items is not a free action.
 */
internal fun restoreConfirmSpec(count: Int): ConfirmSpec = ConfirmSpec(
    titleRes = R.string.trash_confirm_restore_title,
    bodyRes = R.plurals.trash_confirm_restore_body,
    count = count,
    confirmRes = R.string.trash_action_restore,
    destructive = false,
)

internal fun deleteForeverConfirmSpec(count: Int): ConfirmSpec = ConfirmSpec(
    titleRes = R.string.trash_confirm_delete_title,
    bodyRes = R.plurals.trash_confirm_delete_body,
    count = count,
    confirmRes = R.string.trash_action_delete_forever,
    destructive = true,
)

internal fun emptyBinConfirmSpec(count: Int): ConfirmSpec = ConfirmSpec(
    titleRes = R.string.trash_confirm_empty_title,
    bodyRes = R.plurals.trash_confirm_empty_body,
    count = count,
    confirmRes = R.string.trash_action_empty,
    destructive = true,
)

/** Which resource `TrashEntryRow` resolves the countdown from. The arithmetic stays in `:domain`. */
internal sealed interface ExpiryLabel {
    data class DaysLeft(val days: Int) : ExpiryLabel
    data object Today : ExpiryLabel
}

/**
 * `TrashRetention.daysLeft` mapped to a resource choice, so `TrashEntryRow` does no arithmetic of its
 * own — it only resolves whichever id this returns (`R.plurals.trash_expires_in_days` with the count,
 * or the flat `R.string.trash_expires_today` once `daysLeft` floors at 0).
 */
internal fun TrashEntry.expiryLabel(now: Instant): ExpiryLabel {
    val days = TrashRetention.daysLeft(this, now)
    return if (days <= 0) ExpiryLabel.Today else ExpiryLabel.DaysLeft(days)
}
