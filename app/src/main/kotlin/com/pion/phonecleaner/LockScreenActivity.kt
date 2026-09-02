package com.pion.phonecleaner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pion.phonecleaner.core.ui.theme.PhoneCleanerTheme
import com.pion.phonecleaner.feature.applock.lockscreen.LockScreenRoute

/**
 * The lock surface, and the one screen in the app that is **not** a `NavHost` destination
 * (`LLM.md` §7.5): it must appear over *another* app, driven by `ForegroundAppMonitor.lockRequests`
 * in `:data`, never by anything the user taps in ours. A `NavHost` route cannot do that.
 *
 * ### Three manifest attributes, each closing a specific hole
 *
 * `android:exported="false"` — the competitor's equivalent **is** exported and takes an arbitrary
 * `pkg` extra from any app on the device (`AndroidManifest.xml:246`), so any installed app can raise
 * a lock prompt naming any package it likes. Ours can only be started by this app's own process.
 *
 * `android:launchMode="singleTask"` — a second lock prompt must never stack on the first. Without
 * it, switching between two locked apps leaves a pile of prompts that Back walks through one by one.
 *
 * `android:excludeFromRecents="true"` — the recents screenshot of a lock prompt names the app being
 * protected, which is exactly the fact the lock exists to keep off the screen.
 *
 * The target package arrives as one `Intent` extra keyed by `LockScreenArgs.PACKAGE_NAME`, imported
 * from `:feature:applock` rather than repeated as a literal so the compiler checks the one spelling.
 * `ComponentActivity` folds extras into the default `SavedStateHandle`, which is where
 * `LockScreenViewModel` reads it — the ViewModel never sees an `Intent` (`LLM.md` §7.3).
 */
class LockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneCleanerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LockScreenRoute(
                        // The locked app is already underneath; finishing reveals it.
                        onUnlocked = { finish() },
                        onGoHome = {
                            startActivity(
                                Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            finish()
                        },
                    )
                }
            }
        }
    }
}
