package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.feature.network.speedtest.SpeedTestRoute
import com.pion.phonecleaner.feature.network.speedtestresult.SpeedTestResultRoute
import com.pion.phonecleaner.feature.network.traffic.NetworkTrafficRoute

/** Network traffic and the speed test. */
internal fun NavGraphBuilder.networkGraph(navController: NavHostController) {
    composable<Route.NetworkTraffic> {
        NetworkTrafficRoute(onNavigateBack = { navController.popBackStack() })
    }

    composable<Route.SpeedTest> {
        SpeedTestRoute(
            onNavigateToResult = { downloadBps, uploadBps ->
                navController.navigate(Route.SpeedTestResult(downloadBps, uploadBps)) {
                    popUpTo<Route.SpeedTest> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.SpeedTestResult> {
        SpeedTestResultRoute(
            onNavigateToTest = {
                navController.navigate(Route.SpeedTest) {
                    popUpTo<Route.SpeedTestResult> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
