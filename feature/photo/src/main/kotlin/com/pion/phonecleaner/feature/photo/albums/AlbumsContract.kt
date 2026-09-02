package com.pion.phonecleaner.feature.photo.albums

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * `albums` — `docs/screens/13-photo-and-media.md` §6.1. Replaces `ExeestiaActivity` (327 L).
 *
 * No selection, no delete, no dialog: the screen is a directory index and staying that way is the
 * point (§6.2).
 */
data class AlbumsState(
    val phase: ToolPhase = ToolPhase.Idle,
    val albums: ImmutableList<PhotoAlbum> = persistentListOf(),
    val error: AppError? = null,
) : UiState {
    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && albums.isEmpty()
    val totalBytes: Long get() = albums.sumOf { it.totalBytes }
}

sealed interface AlbumsIntent : UiIntent {
    data object ScreenStarted : AlbumsIntent
    data class AlbumOpened(val folderName: String) : AlbumsIntent
    data object CompletionAnimationFinished : AlbumsIntent
    data object BackPressed : AlbumsIntent
}

sealed interface AlbumsEffect : UiEffect {
    data class OpenAlbum(val folderName: String) : AlbumsEffect
    data object NavigateBack : AlbumsEffect
}
