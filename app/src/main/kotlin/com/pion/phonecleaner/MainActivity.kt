package com.pion.phonecleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pion.phonecleaner.core.ui.theme.PhoneCleanerTheme
import com.pion.phonecleaner.navigation.AppNavGraph
import com.pion.phonecleaner.navigation.launchSource

/**
 * The ONLY reader of an Intent in the whole app (LLM.md §3.9).
 *
 * Everything downstream sees `LaunchSource`, so a notification tap and a launcher tap differ in one
 * mapping rather than in scattered `getStringExtra` calls the way the competitor's do.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchSource = intent.launchSource()
        setContent {
            PhoneCleanerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavGraph(launchSource = launchSource)
                }
            }
        }
    }
}
