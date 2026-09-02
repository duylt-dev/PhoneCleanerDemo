package com.pion.phonecleaner.feature.photo

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

/**
 * The selection arithmetic four screens in this cluster share, and the one place it survives process
 * death.
 *
 * **The selection is user input; the photo list is derived data** (`docs/screens/13-photo-and-media.md`
 * §0.5). So the ids go through `SavedStateHandle` and the rows are re-queried — the competitor keeps
 * both on public mutable Activity fields, never reads `savedInstanceState`, and therefore restarts
 * every scan on rotation.
 *
 * `LongArray` because that is what a `SavedStateHandle` can actually carry; `PhotoId` is a
 * `@JvmInline value class` over the `MediaStore` `_id`, so nothing is lost in the round trip.
 */
internal fun SavedStateHandle.readSelection(key: String): ImmutableSet<PhotoId> =
    get<LongArray>(key)?.mapTo(mutableSetOf()) { PhotoId(it) }?.toImmutableSet() ?: persistentSetOf()

internal fun SavedStateHandle.writeSelection(key: String, ids: Set<PhotoId>) {
    set(key, ids.map { it.value }.toLongArray())
}

/** Add if absent, remove if present. Set arithmetic — nothing walks the library per tap. */
internal fun ImmutableSet<PhotoId>.toggle(id: PhotoId): ImmutableSet<PhotoId> =
    (if (id in this) this - id else this + id).toImmutableSet()

/**
 * A whole month header: select every member if any is unselected, clear it if all are already
 * selected. The header itself is never a row in the selection — it is a grid span, so it cannot be
 * selected and cannot reach an engine with an empty path (§5.1).
 */
internal fun ImmutableSet<PhotoId>.toggleGroup(group: PhotoGroup): ImmutableSet<PhotoId> {
    val ids = group.photos.map { it.id }
    return if (ids.all { it in this }) (this - ids.toSet()).toImmutableSet()
    else (this + ids).toImmutableSet()
}

/** Every photo in every group, for a screen-wide select-all. */
internal fun allIds(groups: List<PhotoGroup>): ImmutableSet<PhotoId> =
    groups.flatMap { group -> group.photos.map(Photo::id) }.toImmutableSet()

/** True when [group] has at least one member and every member is selected. */
internal fun ImmutableSet<PhotoId>.isGroupFullySelected(group: PhotoGroup): Boolean =
    group.photos.isNotEmpty() && group.photos.all { it.id in this }
