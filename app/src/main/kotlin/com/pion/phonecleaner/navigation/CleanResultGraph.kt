package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.feature.cleanresult.CleanResultRoute

/** The one shared result screen that fifteen tools end on. */
internal fun NavGraphBuilder.cleanResultGraph(navController: NavHostController) {
    composable<Route.CleanResult> { entry ->
        CleanResultRoute(
            summary = entry.toRoute<Route.CleanResult>().toSummary(),
            // The result screen replaces itself: the next tool must not have this one behind it.
            onNavigateToFeature = { feature: FeatureId ->
                navController.navigate(feature.destination()) {
                    popUpTo<Route.CleanResult> { inclusive = true }
                }
            },
            onNavigateHome = { navController.popBackStack<Route.Home>(inclusive = false) },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
