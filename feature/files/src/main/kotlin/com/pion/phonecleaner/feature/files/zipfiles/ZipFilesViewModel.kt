package com.pion.phonecleaner.feature.files.zipfiles

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.fold
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.ZipFileRequest
import com.pion.phonecleaner.domain.usecase.CreateZipFileUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ZipFilesViewModel(
    private val createZipFile: CreateZipFileUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
) : MviViewModel<ZipFilesState, ZipFilesIntent, ZipFilesEffect>(ZipFilesState()) {

    override fun onIntent(intent: ZipFilesIntent) {
        when (intent) {
            ZipFilesIntent.ScreenStarted -> launchSafely { markFeatureUsed(FeatureId.ZipFiles) }
            ZipFilesIntent.PickFilesPressed -> sendEffect(ZipFilesEffect.RequestFiles)
            is ZipFilesIntent.FilesPicked -> onFilesPicked(intent)
            ZipFilesIntent.CreateZipPressed -> createZip()
            ZipFilesIntent.CompletionAnimationFinished -> setState { copy(phase = ToolPhase.Ready) }
            ZipFilesIntent.BackPressed -> sendEffect(ZipFilesEffect.NavigateBack)
        }
    }

    private fun onFilesPicked(intent: ZipFilesIntent.FilesPicked) {
        val limited = intent.files.take(CreateZipFileUseCase.MAX_FILES).toImmutableList()
        setState {
            copy(
                phase = ToolPhase.Ready,
                pickedFiles = limited,
                ignoredCount = intent.ignoredCount + (intent.files.size - limited.size).coerceAtLeast(0),
                outcome = null,
                error = null,
            )
        }
    }

    private fun createZip() {
        if (currentState.phase == ToolPhase.Scanning) return
        val files = currentState.pickedFiles
        if (files.isEmpty()) {
            setState { copy(error = AppError.NotFound("zip-input")) }
            return
        }
        setState { copy(phase = ToolPhase.Scanning, outcome = null, error = null) }
        launchSafely(onError = { setState { copy(phase = ToolPhase.Ready, error = it) } }) {
            val request = ZipFileRequest(files = files, fileName = defaultZipName())
            createZipFile(request).fold(
                onSuccess = { outcome ->
                    setState {
                        copy(
                            phase = ToolPhase.Completing,
                            pickedFiles = kotlinx.collections.immutable.persistentListOf(),
                            ignoredCount = 0,
                            outcome = outcome,
                            error = null,
                        )
                    }
                },
                onFailure = { error -> setState { copy(phase = ToolPhase.Ready, error = error) } },
            )
        }
    }

    private fun defaultZipName(): String = "phone_cleaner_${LocalDateTime.now().format(ZIP_NAME_FORMAT)}.zip"

    private companion object {
        val ZIP_NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    }
}
