package com.example.dashpilot // Verify this matches your package

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GigPilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // ...
        com.example.dashpilot.util.GigLogger.init(this) // <--- Add this
    }
}