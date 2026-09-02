package com.pion.phonecleaner.feature.network.speedtest

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.model.network.SpeedSample

/**
 * The pure transitions of [SpeedTestState]. Each one sets [SpeedTestState.phase] explicitly, because
 * a phase enum's one failure mode is a path that forgets to leave the busy state.
 */

internal fun SpeedTestState.withStarted(): SpeedTestState = copy(
    phase = SpeedTestState.Phase.Running,
    progressPercent = 0,
    currentBytesPerSecond = null,
    error = null,
)

internal fun SpeedTestState.withSample(sample: SpeedSample): SpeedTestState = copy(
    progressPercent = sample.progressPercent.coerceIn(0, 100),
    stage = sample.stage,
    currentBytesPerSecond = sample.instantBytesPerSecond,
)

/**
 * No byte source is configured. **No figure is written**: [SpeedTestState.currentBytesPerSecond]
 * goes back to `null` rather than to `0`, so nothing downstream can render an unmeasured zero.
 */
internal fun SpeedTestState.withNotConfigured(): SpeedTestState = copy(
    phase = SpeedTestState.Phase.NotConfigured,
    progressPercent = 0,
    currentBytesPerSecond = null,
    isAbandonPromptVisible = false,
    error = null,
)

internal fun SpeedTestState.withFinished(): SpeedTestState = copy(
    phase = SpeedTestState.Phase.Finished,
    progressPercent = 100,
    isAbandonPromptVisible = false,
    error = null,
)

internal fun SpeedTestState.withFailure(error: AppError): SpeedTestState = copy(
    phase = SpeedTestState.Phase.Finished,
    currentBytesPerSecond = null,
    isAbandonPromptVisible = false,
    error = error,
)
