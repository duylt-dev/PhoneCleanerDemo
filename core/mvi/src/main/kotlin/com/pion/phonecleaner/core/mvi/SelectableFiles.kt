package com.pion.phonecleaner.core.mvi

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

/**
 * Selection is a `Set` of ids on the state, never an `isSelected` field on the model.
 *
 * A mutated item is `equals` its predecessor inside the old list, so no diff can see it and the row
 * never redraws (LLM.md §8). Keeping selection beside the list instead of inside it makes that
 * failure impossible to write.
 */
@Immutable
data class SelectableFiles<T>(
    val items: ImmutableList<T>,
    val selectedIds: ImmutableSet<String> = persistentSetOf(),
    private val idOf: (T) -> String,
) {
    val selectedCount: Int get() = selectedIds.size

    val isAllSelected: Boolean get() = items.isNotEmpty() && selectedIds.size == items.size

    fun isSelected(item: T): Boolean = idOf(item) in selectedIds

    fun toggle(id: String): SelectableFiles<T> = copy(
        selectedIds = if (id in selectedIds) {
            (selectedIds - id).toImmutableSet()
        } else {
            (selectedIds + id).toImmutableSet()
        },
    )

    fun selectAll(): SelectableFiles<T> = copy(selectedIds = items.map(idOf).toImmutableSet())

    fun clearSelection(): SelectableFiles<T> = copy(selectedIds = persistentSetOf())

    fun selectedItems(): List<T> = items.filter { idOf(it) in selectedIds }
}
