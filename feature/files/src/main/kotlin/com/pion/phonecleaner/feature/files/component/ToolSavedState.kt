package com.pion.phonecleaner.feature.files.component

import androidx.lifecycle.SavedStateHandle
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

/**
 * The selection, across process death.
 *
 * The item list is **derived data** and is re-scanned; the selection is not, so it is the only thing
 * saved (`docs/screens/14-file-tools-and-app-manager.md` §1.2). The competitor loses both on a
 * rotation, because both live in Activity fields.
 *
 * `ArrayList<String>` rather than a `Set`: `SavedStateHandle` is backed by a `Bundle`, and a `Bundle`
 * has no set writer. One place converts, so no screen has to remember that.
 */
internal fun SavedStateHandle.restoreSelection(key: String = SelectionKey): ImmutableSet<String> =
    get<ArrayList<String>>(key)?.toImmutableSet() ?: persistentSetOf()

internal fun SavedStateHandle.storeSelection(ids: Set<String>, key: String = SelectionKey) {
    set(key, ArrayList(ids))
}

private const val SelectionKey = "files.selectedIds"
