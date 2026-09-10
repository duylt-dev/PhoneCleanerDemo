package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pion.phonecleaner.feature.cleanresult.CleanResultRoute

/**
 * The one shared result screen that fifteen tools end on.
 *
 * **It has no forward edge.** The route used to take an `onNavigateToFeature` that popped itself and
 * opened whatever suggestion the user tapped; the suggestion list was removed by owner decision
 * (2026-09-03), so Home and Back are the only ways out and `FeatureId.destination()` is reached from
 * `homeGraph` alone.
 */
internal fun NavGraphBuilder.cleanResultGraph(navController: NavHostController) {
    composable<Route.CleanResult> { entry ->
        CleanResultRoute(
            summary = entry.toRoute<Route.CleanResult>().toSummary(),
            onNavigateHome = { navController.popBackStack<Route.Home>(inclusive = false) },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
