package com.pion.phonecleaner.feature.photo.albums

import com.pion.phonecleaner.core.ui.component.list.FolderFilterTab
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun ImmutableList<PhotoAlbum>.sortedBy(sort: AlbumSort): ImmutableList<PhotoAlbum> =
    when (sort) {
        AlbumSort.MostItems -> sortedWith(compareByDescending<PhotoAlbum> { it.count }.thenBy { it.label.lowercase() })
        AlbumSort.LargestFirst -> sortedWith(
            compareByDescending<PhotoAlbum> { it.totalBytes }
                .thenBy { it.label.lowercase() },
        )
        AlbumSort.Name -> sortedBy { it.label.lowercase() }
    }.toImmutableList()

internal fun ImmutableList<PhotoAlbum>.folderTabs(allLabel: String): ImmutableList<FolderFilterTab> {
    val folders = sortedWith(compareByDescending<PhotoAlbum> { it.count }.thenBy { it.label.lowercase() })
        .map { FolderFilterTab(key = it.folderName, label = it.label, count = it.count) }
    return (listOf(FolderFilterTab(key = null, label = allLabel, count = sumOf { it.count })) + folders)
        .toImmutableList()
}
