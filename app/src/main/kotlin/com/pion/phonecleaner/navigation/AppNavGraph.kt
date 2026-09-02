package com.pion.phonecleaner.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.pion.phonecleaner.data.permission.SpecialAccessIntents
import com.pion.phonecleaner.domain.model.launch.LaunchSource
import com.pion.phonecleaner.domain.model.permission.AppPermission

/**
 * The single NavHost. Every `XRoute`'s callbacks are wired to `navController` calls HERE, so no
 * feature module needs to know another exists (LLM.md §3.9, §7).
 *
 * Navigation is always an Effect a ViewModel raises, never a flag on state
 * (`.claude/CLAUDE.md`, non-negotiable rule 6).
 *
 * ### The shape of this file
 *
 * The 44 destinations are wired by one `NavGraphBuilder` extension per feature cluster, each in its
 * own file in this package. This function owns only what is genuinely host-wide — the back stack,
 * the special-access launcher and the exit — and hands those to the builders. Adding a screen means
 * editing that cluster's builder, not a 450-line composable that every cluster shares.
 *
 * ### Two rules the wiring repeats
 *
 * **A scan screen pops itself.** Every `onScanned` navigates with `popUpTo(<the scan>) { inclusive
 * = true }`, so Back from a result never replays the scan the user just sat through, and a rescan
 * cannot stack a second result behind the first.
 *
 * **`onSessionLost` is the process-death branch.** A screen whose payload lives in a session store
 * rather than in its route argument has no way to rebuild itself after the process is killed; it
 * says so instead of rendering an empty list (`docs/system-architecture.md` §5.2).
 */
@Composable
fun AppNavGraph(
    launchSource: LaunchSource,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // The host owns every special access; `HomeRoute` and `AppLockRoute` own only the one runtime
    // permission each can request in-screen. The result is ignored on purpose — the screens re-read
    // the grant on resume, which is the only answer that is true after the user leaves Settings.
    val specialAccess = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val onRequestSpecialAccess = remember(context) {
        { permission: AppPermission ->
            specialAccess.launch(SpecialAccessIntents.forPermission(context, permission))
        }
    }
    val onExit = remember(context) { { context.findActivity()?.finish(); Unit } }

    NavHost(
        navController = navController,
        startDestination = Route.Splash(launchSource),
        modifier = modifier,
    ) {
        onboardingGraph(navController)
        homeGraph(navController, onRequestSpecialAccess, onExit)
        junkGraph(navController)
        photoGraph(navController)
        fileToolsGraph(navController)
        cleanResultGraph(navController)
        antivirusGraph(navController)
        appLockGraph(navController, onRequestSpecialAccess)
        notificationGraph(navController)
        deviceGraph(navController)
        networkGraph(navController)
        settingsGraph(navController)
    }
}
