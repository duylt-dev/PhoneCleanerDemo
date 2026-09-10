package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.domain.model.junk.JunkScanMode
import com.pion.phonecleaner.feature.device.batteryinfo.BatteryInfoRoute
import com.pion.phonecleaner.feature.device.batteryscan.BatteryScanRoute
import com.pion.phonecleaner.feature.device.devicestatusdetail.DeviceStatusDetailRoute
import com.pion.phonecleaner.feature.device.devicestatusscan.DeviceStatusScanRoute
import com.pion.phonecleaner.feature.device.runningapps.RunningAppsRoute
import com.pion.phonecleaner.feature.device.runningappsscan.RunningAppsScanRoute

/** Device status, battery and running apps — three scan/detail pairs. */
internal fun NavGraphBuilder.deviceGraph(navController: NavHostController) {
    composable<Route.DeviceStatusScan> {
        DeviceStatusScanRoute(
            onScanned = {
                navController.navigate(Route.DeviceStatusDetail) {
                    popUpTo<Route.DeviceStatusScan> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.DeviceStatusDetail> {
        DeviceStatusDetailRoute(
            onNavigateToRunningApps = { navController.navigate(Route.RunningAppsScan) },
            onNavigateToJunkClean = {
                navController.navigate(Route.JunkScan(JunkScanMode.Review))
            },
            onNavigateToBattery = { navController.navigate(Route.BatteryScan) },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.BatteryScan> {
        BatteryScanRoute(
            onScanned = {
                navController.navigate(Route.BatteryInfo) {
                    popUpTo<Route.BatteryScan> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.BatteryInfo> {
        BatteryInfoRoute(onNavigateBack = { navController.popBackStack() })
    }

    composable<Route.RunningAppsScan> {
        RunningAppsScanRoute(
            onScanned = {
                navController.navigate(Route.RunningApps) {
                    popUpTo<Route.RunningAppsScan> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.RunningApps> {
        RunningAppsRoute(onNavigateBack = { navController.popBackStack() })
    }
}
