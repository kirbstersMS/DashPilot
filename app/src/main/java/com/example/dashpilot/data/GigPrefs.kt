package com.example.dashpilot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GigPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gig_pilot_prefs", Context.MODE_PRIVATE)

    // --- REACTIVE STATE ---
    private val _isScrapingEnabledFlow = MutableStateFlow(prefs.getBoolean("is_scraping_enabled", true))
    val isScrapingEnabledFlow: StateFlow<Boolean> = _isScrapingEnabledFlow.asStateFlow()

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "is_scraping_enabled" -> {
                val newValue = sharedPreferences.getBoolean(key, true)
                _isScrapingEnabled = newValue
                _isScrapingEnabledFlow.value = newValue
            }
            // Add listeners for other keys if you want instant UI updates elsewhere
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    // --- HIGH FREQUENCY VARIABLES ---
    private var _isScrapingEnabled = prefs.getBoolean("is_scraping_enabled", true)
    var isScrapingEnabled: Boolean
        get() = _isScrapingEnabled
        set(value) = prefs.edit { putBoolean("is_scraping_enabled", value) }

    // --- THE BINARY STRATEGY (NEW) ---
    // Simple Pass/Fail Targets. No more "ranges".

    var targetDollarsPerMile: Float
        get() = prefs.getFloat("target_dlr_mile", 2.0f) // Default $2/mi
        set(value) = prefs.edit { putFloat("target_dlr_mile", value) }

    var targetDollarsPerHour: Float
        get() = prefs.getFloat("target_dlr_hour", 25.0f) // Default $25/hr
        set(value) = prefs.edit { putFloat("target_dlr_hour", value) }

    // --- OVERLAY & THEME ---
    var overlayX: Int
        get() = prefs.getInt("overlay_x", 20)
        set(value) = prefs.edit { putInt("overlay_x", value) }

    var overlayY: Int
        get() = prefs.getInt("overlay_y", 50)
        set(value) = prefs.edit { putInt("overlay_y", value) }

    var isOverlayDarkTheme: Boolean
        get() = prefs.getBoolean("is_overlay_dark_theme", true)
        set(value) = prefs.edit { putBoolean("is_overlay_dark_theme", value) }

    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1.0f)
        set(value) = prefs.edit { putFloat("font_scale", value) }

    var isAppDarkTheme: Boolean
        get() = prefs.getBoolean("is_app_dark_theme", true)
        set(value) = prefs.edit { putBoolean("is_app_dark_theme", value) }

    var isDebugLoggingEnabled: Boolean
        get() = prefs.getBoolean("debug_logging_enabled", false)
        set(value) {
            prefs.edit { putBoolean("debug_logging_enabled", value) }
            com.example.dashpilot.util.GigLogger.setLoggingEnabled(value)
        }
}