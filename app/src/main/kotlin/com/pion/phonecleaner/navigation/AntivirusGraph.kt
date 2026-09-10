package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.feature.antivirus.result.AntivirusResultRoute
import com.pion.phonecleaner.feature.antivirus.scan.AntivirusScanRoute

/** The antivirus scan and its result. */
internal fun NavGraphBuilder.antivirusGraph(navController: NavHostController) {
    composable<Route.AntivirusScan> {
        AntivirusScanRoute(
            onNavigateToResult = { findingCount ->
                navController.navigate(Route.AntivirusResult(findingCount)) {
                    popUpTo<Route.AntivirusScan> { inclusive = true }
                }
            },
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.AntivirusResult> {
        AntivirusResultRoute(
            // The popUpTo is what the competitor's `finish()` did: a rescan does not stack a
            // second result behind the first.
            onNavigateToScan = {
                navController.navigate(Route.AntivirusScan) {
                    popUpTo<Route.AntivirusResult> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
