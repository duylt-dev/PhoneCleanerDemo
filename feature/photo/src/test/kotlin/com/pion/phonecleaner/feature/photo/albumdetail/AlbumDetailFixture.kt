package com.pion.phonecleaner.feature.photo.albumdetail

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.usecase.DeletePhotosUseCase
import com.pion.phonecleaner.domain.usecase.LoadAlbumPhotosUseCase
import com.pion.phonecleaner.feature.photo.testing.FakePhotoRepository
import com.pion.phonecleaner.feature.photo.testing.RecordingAnalytics
import com.pion.phonecleaner.feature.photo.testing.photo
import kotlinx.collections.immutable.persistentListOf

/**
 * The album fixture both `albumdetail` test classes share — the screen's own rows plus one row in a
 * neighbouring folder, which is what proves the query is keyed on the **full parent path** and not
 * on the last segment (`docs/screens/13-photo-and-media.md` §6.5).
 */
internal class AlbumDetailFixture {

    val folder = "/storage/emulated/0/DCIM"
    val first = photo(1, sizeBytes = 500L, folderName = folder)
    val second = photo(2, sizeBytes = 300L, folderName = folder)
    private val elsewhere = photo(3, sizeBytes = 700L, folderName = "/storage/emulated/0/Pictures")

    val repository = FakePhotoRepository(library = listOf(first, second, elsewhere))
    val analytics = RecordingAnalytics()

    fun viewModel() = AlbumDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(AlbumDetailViewModel.FOLDER_NAME_ARG to folder)),
        loadAlbumPhotos = LoadAlbumPhotosUseCase(repository),
        deletePhotos = DeletePhotosUseCase(repository),
        analytics = analytics,
    )

    fun loaded() = viewModel().also { it.onIntent(AlbumDetailIntent.ScreenStarted) }

    fun selectedFirst() = loaded().also { it.onIntent(AlbumDetailIntent.PhotoToggled(PhotoId(1))) }

    /** The ordinary API 30+ answer: the system, not this app, will ask the user. */
    fun deleteRaisesConsent(token: PendingIntentToken) {
        repository.nextDelete = AppResult.Success(
            DeleteOutcome.PendingConsent(request = token, ids = persistentListOf(first.contentUri)),
        )
    }
}
