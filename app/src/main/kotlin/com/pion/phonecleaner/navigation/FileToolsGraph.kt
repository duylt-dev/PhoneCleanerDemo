package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.feature.files.appmanager.AppManagerRoute
import com.pion.phonecleaner.feature.files.audio.AudioManagerRoute
import com.pion.phonecleaner.feature.files.bigfiles.BigFilesRoute
import com.pion.phonecleaner.feature.files.duplicates.DuplicatesRoute
import com.pion.phonecleaner.feature.files.video.VideoManagerRoute
import com.pion.phonecleaner.feature.files.whatsapp.WhatsAppCleanerRoute

/** The six file tools. Every one of them ends on the shared clean-result screen. */
internal fun NavGraphBuilder.fileToolsGraph(navController: NavHostController) {
    composable<Route.BigFiles> {
        BigFilesRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.Duplicates> {
        DuplicatesRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.VideoManager> {
        VideoManagerRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.AudioManager> {
        AudioManagerRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.AppManager> {
        AppManagerRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.WhatsAppCleaner> {
        WhatsAppCleanerRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
