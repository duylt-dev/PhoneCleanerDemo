package com.pion.phonecleaner.feature.files.zipfiles

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.file.ZipFileOutcome
import com.pion.phonecleaner.domain.model.file.ZipInputFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class ZipFilesState(
    val phase: ToolPhase = ToolPhase.Idle,
    val pickedFiles: ImmutableList<ZipInputFile> = persistentListOf(),
    val ignoredCount: Int = 0,
    val outcome: ZipFileOutcome? = null,
    val error: AppError? = null,
) : UiState {
    val canCreate: Boolean get() = phase == ToolPhase.Ready && pickedFiles.isNotEmpty()
    val isBusy: Boolean get() = phase == ToolPhase.Scanning
    val selectedBytes: Long get() = pickedFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
}

sealed interface ZipFilesIntent : UiIntent {
    data object ScreenStarted : ZipFilesIntent
    data object PickFilesPressed : ZipFilesIntent
    data class FilesPicked(
        val files: ImmutableList<ZipInputFile>,
        val ignoredCount: Int = 0,
    ) : ZipFilesIntent
    data object CreateZipPressed : ZipFilesIntent
    data object CompletionAnimationFinished : ZipFilesIntent
    data object BackPressed : ZipFilesIntent
}

sealed interface ZipFilesEffect : UiEffect {
    data object RequestFiles : ZipFilesEffect
    data object NavigateBack : ZipFilesEffect
}
