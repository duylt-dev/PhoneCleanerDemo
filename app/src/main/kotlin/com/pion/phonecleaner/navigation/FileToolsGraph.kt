package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.feature.files.appmanager.AppManagerRoute
import com.pion.phonecleaner.feature.files.audio.AudioManagerRoute
import com.pion.phonecleaner.feature.files.bigfiles.BigFilesRoute
import com.pion.phonecleaner.feature.files.duplicates.DuplicatesRoute
import com.pion.phonecleaner.feature.files.video.VideoManagerRoute
import com.pion.phonecleaner.feature.files.videocompressor.VideoCompressorRoute
import com.pion.phonecleaner.feature.files.videocompressrun.VideoCompressRunRoute
import com.pion.phonecleaner.feature.files.whatsapp.WhatsAppCleanerRoute
import com.pion.phonecleaner.feature.files.zipfiles.ZipFilesRoute

/** The six file tools and the video-compression pair. Every one of them ends on the shared clean-result screen. */
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

    composable<Route.ZipFiles> {
        ZipFilesRoute(onNavigateBack = { navController.popBackStack() })
    }

    composable<Route.VideoCompressor> {
        VideoCompressorRoute(
            onOpenCompressRun = { ids, preset, codec ->
                navController.navigate(Route.VideoCompressRun(ids, preset, codec))
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.VideoCompressRun> {
        VideoCompressRunRoute(
            onNavigateToCleanResult = { summary ->
                navController.navigate(Route.CleanResult.of(summary)) {
                    // After the originals are gone the picker's list is stale by definition: it
                    // lists rows that no longer exist. Same rule PhotoGraph applies to `CompressRun`.
                    popUpTo<Route.VideoCompressor> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
