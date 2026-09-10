package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.domain.model.photo.PhotoSessionSource
import com.pion.phonecleaner.feature.photo.albumdetail.AlbumDetailRoute
import com.pion.phonecleaner.feature.photo.albums.AlbumsRoute
import com.pion.phonecleaner.feature.photo.blurry.BlurryPhotosRoute
import com.pion.phonecleaner.feature.photo.compressor.PhotoCompressorRoute
import com.pion.phonecleaner.feature.photo.compressrun.CompressRunRoute
import com.pion.phonecleaner.feature.photo.preview.PhotoPreviewRoute
import com.pion.phonecleaner.feature.photo.privacy.PhotoPrivacyRoute
import com.pion.phonecleaner.feature.photo.similar.SimilarPhotosRoute

/** Albums, similar photos, blurry photos, the preview pager, the compressor and the privacy cleaner. */
internal fun NavGraphBuilder.photoGraph(navController: NavHostController) {
    composable<Route.PhotoAlbums> {
        AlbumsRoute(
            onOpenAlbum = { navController.navigate(Route.AlbumDetail(it)) },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.AlbumDetail> {
        AlbumDetailRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.SimilarPhotos> {
        SimilarPhotosRoute(
            onOpenPreview = { groupKey, startIndex ->
                navController.navigate(
                    Route.PhotoPreview(groupKey, startIndex, PhotoSessionSource.Similar),
                )
            },
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.BlurryPhotos> {
        BlurryPhotosRoute(
            // The SAME pager, told which session it is over. The two grids keep separate stores.
            onOpenPreview = { groupKey, startIndex ->
                navController.navigate(
                    Route.PhotoPreview(groupKey, startIndex, PhotoSessionSource.Blurry),
                )
            },
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.PhotoPreview> {
        PhotoPreviewRoute(
            onNavigateBack = { navController.popBackStack() },
            onSessionLost = { navController.popBackStack() },
        )
    }

    composable<Route.PhotoCompressor> {
        PhotoCompressorRoute(
            onOpenCompressRun = { navController.navigate(Route.CompressRun(it)) },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.CompressRun> {
        CompressRunRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.PhotoPrivacy> {
        PhotoPrivacyRoute(
            onNavigateToCleanResult = navController.toCleanResult(),
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
