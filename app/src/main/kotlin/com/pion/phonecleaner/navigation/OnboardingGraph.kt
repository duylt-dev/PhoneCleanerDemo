package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import com.pion.phonecleaner.feature.onboarding.appresume.AppResumeRoute
import com.pion.phonecleaner.feature.onboarding.devicecheck.DeviceCheckRoute
import com.pion.phonecleaner.feature.onboarding.splash.SplashRoute

/** Splash, the device check and the app-resume dialog. */
internal fun NavGraphBuilder.onboardingGraph(navController: NavHostController) {
    composable<Route.Splash> {
        SplashRoute(
            onNavigateToDeviceCheck = {
                navController.navigate(Route.DeviceCheck) {
                    popUpTo<Route.Splash> { inclusive = true }
                }
            },
            onNavigateToHome = { navController.toHome<Route.Splash>() },
            onOpenPolicyPage = { navController.navigate(Route.WebView(it)) },
        )
    }

    composable<Route.DeviceCheck> {
        DeviceCheckRoute(onNavigateToHome = { navController.toHome<Route.DeviceCheck>() })
    }

    // A dialog on the existing back stack, not an Activity started from Application scope the
    // way the competitor's is (`Condfil.java:858-868`). Nothing navigates here yet: the gate
    // that decides when it opens sits on pending owner decision 4.
    dialog<Route.AppResume> {
        AppResumeRoute(onDismiss = { navController.popBackStack() })
    }
}
