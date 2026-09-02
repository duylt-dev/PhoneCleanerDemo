package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.feature.notification.gate.NotificationGateRoute
import com.pion.phonecleaner.feature.notification.hiddenlist.HiddenNotificationsRoute
import com.pion.phonecleaner.feature.notification.hidingsettings.NotificationHidingSettingsRoute
import com.pion.phonecleaner.feature.notification.permissionmanager.PermissionManagerRoute

/** The notification cleaner and the permission manager. */
internal fun NavGraphBuilder.notificationGraph(navController: NavHostController) {
    composable<Route.NotificationGate> {
        NotificationGateRoute(
            onNavigateBack = { navController.popBackStack() },
            // The gate is not a screen to come back to.
            onNavigateToHiddenList = {
                navController.navigate(Route.HiddenNotifications) {
                    popUpTo<Route.NotificationGate> { inclusive = true }
                }
            },
        )
    }

    composable<Route.HiddenNotifications> {
        HiddenNotificationsRoute(
            onNavigateBack = { navController.popBackStack() },
            // The Int is a notification COUNT, not bytes — which is why `CleanupSummary` carries
            // `itemCount` beside `freedBytes` and the result screen renders no size block for it.
            onNavigateToCleanResult = { clearedCount ->
                navController.navigate(
                    Route.CleanResult(
                        feature = FeatureId.NotificationCleaner,
                        freedBytes = 0L,
                        itemCount = clearedCount,
                        outcome = CleanupOutcome.ItemsCleared,
                    ),
                )
            },
            onOpenHidingSettings = { navController.navigate(Route.NotificationHidingSettings) },
        )
    }

    composable<Route.NotificationHidingSettings> {
        NotificationHidingSettingsRoute(onNavigateBack = { navController.popBackStack() })
    }

    composable<Route.PermissionManager> {
        PermissionManagerRoute(onNavigateBack = { navController.popBackStack() })
    }
}
