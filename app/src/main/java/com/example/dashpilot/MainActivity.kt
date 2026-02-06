package com.example.dashpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.example.dashpilot.data.GigPrefs
import com.example.dashpilot.ui.MainScreen
import com.example.dashpilot.ui.overlay.AppSwitcherController
import com.example.dashpilot.ui.overlay.OverlayController
import com.example.dashpilot.ui.theme.GigPilotTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@Suppress("AssignedValueIsNeverRead")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // VIBE CHECK: Hilt now injects these singleton instances
    @Inject lateinit var overlayController: OverlayController
    @Inject lateinit var appSwitcherController: AppSwitcherController
    @Inject lateinit var prefs: GigPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // REMOVED: val prefs = GigPrefs(applicationContext)

        setContent {
            // State: Holds the current theme.
            // Using the injected 'prefs' here
            var isDarkTheme by remember { mutableStateOf(prefs.isAppDarkTheme) }

            GigPilotTheme(darkTheme = isDarkTheme) {
                Surface {
                    MainScreen(
                        overlayController = overlayController,
                        appSwitcherController = appSwitcherController,
                        isAppDark = isDarkTheme,
                        onAppThemeChange = { newTheme ->
                            isDarkTheme = newTheme
                            prefs.isAppDarkTheme = newTheme
                        }
                    )
                }
            }
        }
    }
}