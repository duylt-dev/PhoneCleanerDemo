package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.feature.home.HomeRoute

/** The home screen — the only destination that can request a special access and exit the app. */
internal fun NavGraphBuilder.homeGraph(
    navController: NavHostController,
    onRequestSpecialAccess: (AppPermission) -> Unit,
    onExit: () -> Unit,
) {
    composable<Route.Home> {
        HomeRoute(
            onOpenFeature = { navController.navigate(it.destination()) },
            onOpenSettings = { navController.navigate(Route.Settings) },
            // The warning banner is about OUR grants, so it opens this app's own roster — not
            // `Route.PermissionManager`, which browses other apps' permissions.
            onOpenPermissionCentre = { navController.navigate(Route.PermissionCentre) },
            onOpenLegal = { navController.navigate(Route.WebView(it)) },
            onRequestSpecialAccess = onRequestSpecialAccess,
            onExit = onExit,
        )
    }
}
