package com.pion.phonecleaner.navigation

import androidx.navigation.NavHostController
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary

/**
 * Helpers shared by the per-cluster graph builders in this package.
 *
 * They are `internal`, not `private`, only because the graph is split across files; nothing outside
 * `:app` may see them.
 */

/**
 * Home is the one destination reached by *clearing* what is above it rather than by pushing a copy:
 * a second `Home` on the stack is how the competitor ends up with a feature screen on top of a
 * half-built home (delta 9).
 */
internal inline fun <reified T : Route> NavHostController.toHome() {
    navigate(Route.Home()) { popUpTo<T> { inclusive = true } }
}

/**
 * Fifteen screens end the same way. Written once so the destination cannot drift between them.
 */
internal fun NavHostController.toCleanResult(): (CleanupSummary) -> Unit =
    { summary -> navigate(Route.CleanResult.of(summary)) }
