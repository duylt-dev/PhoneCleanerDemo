package com.pion.phonecleaner.feature.files.videocompressor

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
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.feature.files.component.MediaKind
import com.pion.phonecleaner.feature.files.component.mediaAccessOf
import com.pion.phonecleaner.feature.files.component.mediaPermissions
import kotlinx.collections.immutable.ImmutableList
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1) —
 * `onOpenCompressRun` carries the three scalars `VideoCompressorEffect.OpenVideoCompressRun` declares,
 * and `:app` is the only place that turns them into `Route.VideoCompressRun` (phase 08).
 *
 * **The permission is resolved HERE**, mirroring `VideoManagerRoute` structurally: `android.os.Build`
 * and `checkSelfPermission` are platform truth, and a ViewModel that imported either would not run on
 * a bare JVM (MVI §4).
 *
 * `LifecycleStartEffect` re-resolves the grant on every `ON_START`, which is what makes the return
 * from Settings work: no app is handed a result for that trip.
 */
@Composable
fun VideoCompressorRoute(
    onOpenCompressRun: (ImmutableList<String>, VideoQualityPreset, VideoCodecOption) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoCompressorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // The map's own booleans are not read: `mediaAccessOf` re-reads the real grant, which is the
        // only thing that distinguishes a full grant from Android 14's user-selected one.
        onIntent(VideoCompressorIntent.PermissionResolved(mediaAccessOf(context, MediaKind.Visual)))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            VideoCompressorEffect.RequestMediaPermission ->
                permissionLauncher.launch(mediaPermissions(MediaKind.Visual))

            is VideoCompressorEffect.OpenVideoCompressRun ->
                onOpenCompressRun(effect.ids, effect.preset, effect.codec)

            VideoCompressorEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(VideoCompressorIntent.ScreenStarted)
        onIntent(VideoCompressorIntent.PermissionResolved(mediaAccessOf(context, MediaKind.Visual)))
        onStopOrDispose { }
    }

    // Toolbar back and system back are one rule, not two.
    BackHandler { onIntent(VideoCompressorIntent.BackPressed) }

    VideoCompressorScreen(state = state, onIntent = onIntent, modifier = modifier)
}
