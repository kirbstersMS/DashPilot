package com.example.dashpilot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.dashpilot.MainActivity
import com.example.dashpilot.R
import com.example.dashpilot.data.ShiftTracker
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.core.content.edit

class ShiftTrackingService : Service() {
    private var lastLocation: Location? = null

    // NEW: Use Fused Provider
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // NEW: Restore state if we are restarting from a crash/kill
        val prefs = getSharedPreferences("shift_prefs", MODE_PRIVATE)
        val wasTracking = prefs.getBoolean("is_tracking", false)

        if (wasTracking && !ShiftTracker.isTracking.value) {
            // Restore the singleton from disk
            val savedStart = prefs.getLong("start_time", System.currentTimeMillis())
            val savedMiles = prefs.getFloat("accumulated_miles", 0f)

            ShiftTracker.restoreState(savedStart, savedMiles.toDouble())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Start the Foreground Notification immediately (Required by Android 14)
        val notification = buildNotification("Starting shift...")

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        // 2. Start GPS
        startLocationUpdates()

        return START_STICKY // Restart if killed
    }

    private fun startLocationUpdates() {
        // High Accuracy = GPS, but balanced with other signals
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper // or a background looper if you prefer
            )
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->

                // 1. Accuracy Gate: If the signal is trash, ignore it.
                // standard GPS accuracy is usually 3-10 meters.
                // If it's worse than 20m, it's likely a cell-tower triangulation jump.
                if (location.accuracy > 20f) return

                if (lastLocation == null) {
                    lastLocation = location
                    return
                }

                val meters = location.distanceTo(lastLocation!!)

                // 2. The Anchor Threshold (Drift Guard)
                // We use 15 meters (~50 feet) as the "I actually moved" threshold.
                // This filters out stoplight jitter and walking around the car.
                if (meters > 15f) {
                    val miles = meters * 0.000621371

                    // Sanity check: 5 miles in 5 seconds = 3600 mph.
                    if (miles < 5.0) {
                        ShiftTracker.addMiles(miles)
                        updateNotification()
                        saveStateToDisk()

                        // CRITICAL: We only update the 'Anchor' if we actually moved.
                        // If we moved 2 meters, we ignore it AND keep the old anchor.
                        // This prevents 2m + 2m + 2m noise from adding up.
                        lastLocation = location
                    }
                }
            }
        }
    }

    private fun updateNotification() {
        val miles = String.format(java.util.Locale.getDefault(), "%.1f", ShiftTracker.milesDriven.value)
        val notification = buildNotification("Tracking: $miles mi")
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, notification)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GigPilot Shift Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Make sure this icon exists
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Shift Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun saveStateToDisk() {
        val prefs = getSharedPreferences("shift_prefs", MODE_PRIVATE)
        prefs.edit {
            putBoolean("is_tracking", true)
            putLong(
                "start_time",
                ShiftTracker.getStartTime()
            ) // You'll need to expose this in ShiftTracker
            putFloat("accumulated_miles", ShiftTracker.milesDriven.value.toFloat())
            // apply() is async, commit() is sync. apply is better for perf.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Clear the "safety save" since we are stopping intentionally
        val prefs = getSharedPreferences("shift_prefs", MODE_PRIVATE)
        prefs.edit { clear() }

        ShiftTracker.stopShiftInternal()
    }

    companion object {
        const val CHANNEL_ID = "shift_tracking_channel"
    }
}