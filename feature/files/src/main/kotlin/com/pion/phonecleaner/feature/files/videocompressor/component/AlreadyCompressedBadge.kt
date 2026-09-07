package com.pion.phonecleaner.feature.files.videocompressor.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.files.R

/**
 * The overlay `VideoCompressorScreen` draws on a grid cell instead of editing `VideoCell`, which
 * `video` also renders — a label only this screen has a ledger to justify does not belong on a row
 * two screens share.
 */
@Composable
internal fun AlreadyCompressedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(Spacing.xxs),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(R.string.video_compress_already_compressed),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}
