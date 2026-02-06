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

    // --- REACTIVE STATE (NEW) ---
    // We initialize this with the current value so collectors get it immediately.
    private val _isScrapingEnabledFlow = MutableStateFlow(prefs.getBoolean("is_scraping_enabled", true))
    val isScrapingEnabledFlow: StateFlow<Boolean> = _isScrapingEnabledFlow.asStateFlow()

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "is_scraping_enabled" -> {
                val newValue = sharedPreferences.getBoolean(key, true)
                _isScrapingEnabled = newValue
                _isScrapingEnabledFlow.value = newValue // Emit update
            }
            "overlay_x" -> _overlayX = sharedPreferences.getInt(key, 20)
            "overlay_y" -> _overlayY = sharedPreferences.getInt(key, 50)
            "is_overlay_dark_theme" -> _isOverlayDarkTheme = sharedPreferences.getBoolean(key, true)
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

    // ... [Rest of your variables (overlayX, thresholds, etc) remain exactly the same] ...
    private var _overlayX = prefs.getInt("overlay_x", 20)
    var overlayX: Int
        get() = _overlayX
        set(value) = prefs.edit { putInt("overlay_x", value) }

    private var _overlayY = prefs.getInt("overlay_y", 50)
    var overlayY: Int
        get() = _overlayY
        set(value) = prefs.edit { putInt("overlay_y", value) }

    private var _isOverlayDarkTheme = prefs.getBoolean("is_overlay_dark_theme", true)
    var isOverlayDarkTheme: Boolean
        get() = _isOverlayDarkTheme
        set(value) = prefs.edit { putBoolean("is_overlay_dark_theme", value) }

    var mileLowThreshold: Float
        get() = prefs.getFloat("mile_low", 1.0f)
        set(value) = prefs.edit { putFloat("mile_low", value) }

    var mileHighThreshold: Float
        get() = prefs.getFloat("mile_high", 2.0f)
        set(value) = prefs.edit { putFloat("mile_high", value) }

    var hourlyLowThreshold: Float
        get() = prefs.getFloat("hourly_low", 20.0f)
        set(value) = prefs.edit { putFloat("hourly_low", value) }

    var hourlyHighThreshold: Float
        get() = prefs.getFloat("hourly_high", 30.0f)
        set(value) = prefs.edit { putFloat("hourly_high", value) }

    var minPayThreshold: Float
        get() = prefs.getFloat("min_pay", 6.0f)
        set(value) = prefs.edit { putFloat("min_pay", value) }

    var minPayBundleThreshold: Float
        get() = prefs.getFloat("min_pay_bundle", 15.0f)
        set(value) = prefs.edit { putFloat("min_pay_bundle", value) }

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