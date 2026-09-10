package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.domain.model.applock.PinMode
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.feature.applock.applock.AppLockRoute
import com.pion.phonecleaner.feature.applock.pin.PinRoute
import com.pion.phonecleaner.feature.applock.settings.AppLockSettingsRoute

/** App lock, its PIN screen and its settings. */
internal fun NavGraphBuilder.appLockGraph(
    navController: NavHostController,
    onRequestSpecialAccess: (AppPermission) -> Unit,
) {
    composable<Route.AppLock> {
        AppLockRoute(
            onNavigateToSettings = { navController.navigate(Route.AppLockSettings) },
            onNavigateBack = { navController.popBackStack() },
            onRequestSpecialAccess = onRequestSpecialAccess,
        )
    }

    composable<Route.Pin> {
        PinRoute(
            onNavigateToAppLock = {
                navController.navigate(Route.AppLock) {
                    popUpTo<Route.Pin> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.AppLockSettings> {
        AppLockSettingsRoute(
            onNavigateToChangePin = { navController.navigate(Route.Pin(PinMode.Change)) },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
