package com.pion.phonecleaner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pion.phonecleaner.feature.settings.about.AboutRoute
import com.pion.phonecleaner.feature.settings.language.LanguageRoute
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCentreRoute
import com.pion.phonecleaner.feature.settings.settings.SettingsRoute
import com.pion.phonecleaner.feature.settings.webview.WebViewRoute

/** Settings, language, about, the web view and this app's own permission roster. */
internal fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    composable<Route.Settings> {
        SettingsRoute(
            onNavigateToLanguage = { navController.navigate(Route.Language) },
            onNavigateToAbout = { navController.navigate(Route.About) },
            onNavigateToPermissionCentre = { navController.navigate(Route.PermissionCentre) },
            onNavigateToTrash = { navController.navigate(Route.Trash) },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.Language> {
        LanguageRoute(onNavigateBack = { navController.popBackStack() })
    }

    composable<Route.About> {
        AboutRoute(
            onNavigateToLegalDocument = { navController.navigate(Route.WebView(it)) },
            // UNRESOLVED — `DevToolsRoute` lives in `:feature:settings`' **debug** source set, so
            // naming it here would not compile in release. Wiring it needs an `:app` debug source
            // set of its own; until that exists this is a no-op, and `AboutViewModel` already
            // refuses to raise the effect unless `isDebugBuild`. Recorded in `LLM.md` §11.
            onNavigateToDeveloperTools = { },
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable<Route.WebView> {
        WebViewRoute(onNavigateBack = { navController.popBackStack() })
    }

    composable<Route.PermissionCentre> {
        PermissionCentreRoute(onNavigateBack = { navController.popBackStack() })
    }
}
