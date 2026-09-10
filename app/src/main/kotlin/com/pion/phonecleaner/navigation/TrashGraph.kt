package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.feature.trash.TrashRoute

/** The bin. One destination, and the only cluster whose single screen needs a special-access launcher. */
internal fun NavGraphBuilder.trashGraph(
    navController: NavHostController,
    onRequestSpecialAccess: (AppPermission) -> Unit,
) {
    composable<Route.Trash> {
        TrashRoute(
            onNavigateBack = { navController.popBackStack() },
            onRequestAllFilesAccess = { onRequestSpecialAccess(AppPermission.AllFiles) },
        )
    }
}
