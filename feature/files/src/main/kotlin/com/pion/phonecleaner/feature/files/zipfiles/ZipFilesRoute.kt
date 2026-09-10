package com.pion.phonecleaner.feature.files.zipfiles

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.file.ZipInputFile
import com.pion.phonecleaner.domain.usecase.CreateZipFileUseCase
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

@Composable
fun ZipFilesRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ZipFilesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val limited = uris.take(CreateZipFileUseCase.MAX_FILES)
        onIntent(
            ZipFilesIntent.FilesPicked(
                files = limited.map { context.toZipInputFile(it) }.toImmutableList(),
                ignoredCount = (uris.size - limited.size).coerceAtLeast(0),
            ),
        )
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ZipFilesEffect.RequestFiles -> picker.launch(arrayOf("*/*"))
            ZipFilesEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(ZipFilesIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(ZipFilesIntent.BackPressed) }
    ZipFilesScreen(state = state, onIntent = onIntent, modifier = modifier)
}

private fun Context.toZipInputFile(uri: Uri): ZipInputFile {
    var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    var size = -1L
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) displayName = it.getString(nameIndex) ?: displayName
            if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex)
        }
    }
    return ZipInputFile(uri = uri.toString(), displayName = displayName, sizeBytes = size)
}
