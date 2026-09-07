package com.pion.phonecleaner.feature.files.videocompressor.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressionEstimate
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.feature.files.R

/** The landing panel, drawn over everything while `VideoCompressorState.introVisible` (§4). */
@Composable
internal fun VideoCompressorIntroPanel(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = ScreenGutter, vertical = Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.video_compress_intro_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
            Button(onClick = onStart) { Text(stringResource(R.string.video_compress_intro_action)) }
        }
    }
}

/**
 * Three chips, written out one per constant rather than looped over `entries` — `MediaSortChips`
 * states the reason: a loop pairs each label with a position rather than with its own constant, so a
 * reorder would silently relabel every chip.
 */
@Composable
internal fun VideoPresetChips(
    selected: VideoQualityPreset,
    onSelect: (VideoQualityPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChipRow(stringResource(R.string.video_compress_quality_label), modifier) {
        PresetChip(VideoQualityPreset.Saver, selected, R.string.video_compress_preset_saver, onSelect)
        PresetChip(VideoQualityPreset.Balanced, selected, R.string.video_compress_preset_balanced, onSelect)
        PresetChip(VideoQualityPreset.Quality, selected, R.string.video_compress_preset_quality, onSelect)
    }
}

@Composable
private fun PresetChip(
    value: VideoQualityPreset,
    current: VideoQualityPreset,
    labelRes: Int,
    onSelect: (VideoQualityPreset) -> Unit,
) {
    val onClick = remember(value, onSelect) { { onSelect(value) } }
    FilterChip(selected = value == current, onClick = onClick, label = { Text(stringResource(labelRes)) })
}

/**
 * Two chips, **both always rendered**. HEVC takes `enabled = isHevcAvailable`; when that is `false`
 * the reason line is drawn beneath the row. This composable computes nothing: the flag arrives on
 * `VideoCompressorState` (D6) and nothing here queries a port or reads `Build.VERSION`.
 */
@Composable
internal fun VideoCodecChips(
    selected: VideoCodecOption,
    isHevcAvailable: Boolean,
    onSelect: (VideoCodecOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ChipRow(stringResource(R.string.video_compress_codec_label)) {
            CodecChip(VideoCodecOption.H264, selected, R.string.video_compress_codec_h264, true, onSelect)
            CodecChip(
                VideoCodecOption.Hevc,
                selected,
                R.string.video_compress_codec_hevc,
                isHevcAvailable,
                onSelect,
            )
        }
        if (!isHevcAvailable) {
            Text(
                text = stringResource(R.string.video_compress_codec_hevc_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenGutter, vertical = Spacing.xxs),
            )
        }
    }
}

@Composable
private fun CodecChip(
    value: VideoCodecOption,
    current: VideoCodecOption,
    labelRes: Int,
    enabled: Boolean,
    onSelect: (VideoCodecOption) -> Unit,
) {
    val onClick = remember(value, onSelect) { { onSelect(value) } }
    FilterChip(
        selected = value == current,
        onClick = onClick,
        enabled = enabled,
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun ChipRow(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(vertical = Spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenGutter),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = ScreenGutter, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { content() }
    }
}

/**
 * Renders nothing when the estimate is unusable — it is never presented as a measurement (key
 * insight 3). The partial line is a **plurals**: the count is passed twice, once to select the form
 * and once to fill `%1$d`.
 */
@Composable
internal fun VideoEstimateLine(estimate: VideoCompressionEstimate?, modifier: Modifier = Modifier) {
    if (estimate == null || !estimate.isUsable) return
    val bytes = rememberByteFormat()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = stringResource(
                R.string.video_compress_estimate,
                bytes.size(estimate.estimatedAfterBytes).toString(),
                bytes.size(estimate.beforeBytes).toString(),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (estimate.unmeasuredCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.video_compress_estimate_partial,
                    estimate.unmeasuredCount,
                    estimate.unmeasuredCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
