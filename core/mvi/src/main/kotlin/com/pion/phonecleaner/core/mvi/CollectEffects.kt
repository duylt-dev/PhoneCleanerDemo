package com.pion.phonecleaner.core.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects a ViewModel's effects, lifecycle-aware.
 *
 * `collect`, never `collectLatest`: `collectLatest` cancels the previous handler when a second effect
 * arrives, so two navigations raised in the same frame become one — an effect is silently lost
 * (`.claude/CLAUDE.md`, non-negotiable rule 5).
 *
 * Every declared Effect must be collected by a screen. An Effect nothing collects is a feature that
 * does not happen.
 */
@Composable
fun <E : UiEffect> CollectEffects(
    effects: Flow<E>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: (E) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            effects.collect { handler(it) }
        }
    }
}
