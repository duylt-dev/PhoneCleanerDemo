package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.junk.JunkScanMode
import com.pion.phonecleaner.feature.junk.junkclean.JunkCleanRoute
import com.pion.phonecleaner.feature.junk.junkreview.JunkReviewRoute
import com.pion.phonecleaner.feature.junk.junkscan.JunkScanRoute

/** Junk scan, review and clean. */
internal fun NavGraphBuilder.junkGraph(navController: NavHostController) {
    composable<Route.JunkScan> {
        JunkScanRoute(
            onScanned = {
                navController.navigate(Route.JunkReview) {
                    popUpTo<Route.JunkScan> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.JunkReview> {
        JunkReviewRoute(
            onClean = { navController.navigate(Route.JunkClean) },
            onNavigateBack = { navController.popBackStack() },
            onSessionLost = {
                navController.navigate(Route.JunkScan(JunkScanMode.Review)) {
                    popUpTo<Route.JunkReview> { inclusive = true }
                }
            },
        )
    }

    composable<Route.JunkClean> {
        JunkCleanRoute(
            // The effect carries freedBytes AND failedCount so a run that failed outright can
            // never be reported as a success (Delta C3).
            //
            // UNKNOWN — the deleted-item count. `JunkCleanEffect` carries bytes and failures,
            // not a count, and `docs/screens/12-junk-cleaning.md:618` names only `freedBytes`.
            // `itemCount = 0` renders no item line rather than a fabricated one.
            onCleaned = { freedBytes, _ ->
                navController.navigate(
                    Route.CleanResult(
                        feature = FeatureId.JunkClean,
                        freedBytes = freedBytes,
                        outcome = if (freedBytes > 0L) {
                            CleanupOutcome.Cleaned
                        } else {
                            CleanupOutcome.NothingFound
                        },
                    ),
                ) { popUpTo<Route.JunkClean> { inclusive = true } }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
