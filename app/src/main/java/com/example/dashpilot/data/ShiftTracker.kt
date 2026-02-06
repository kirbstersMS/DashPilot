// app/src/main/java/com/example/gigpilot/data/ShiftTracker.kt

package com.example.dashpilot.data

import android.content.Context
import android.content.Intent
import com.example.dashpilot.service.ShiftTrackingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ShiftTracker {

    // --- State (Observed by UI) ---
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _milesDriven = MutableStateFlow(0.0)
    val milesDriven: StateFlow<Double> = _milesDriven

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds

    // --- Status Tracking ---
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    // Internal state
    private var startTime = 0L
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // --- Public Actions ---

    fun startShift(context: Context) {
        if (_isTracking.value) return

        startTime = System.currentTimeMillis()
        _milesDriven.value = 0.0
        _durationSeconds.value = 0
        _isTracking.value = true
        _isBusy.value = false // Default to Searching

        // Log START & SEARCHING
        logStatus(context, "SHIFT_START")
        logStatus(context, "SEARCHING")

        startTimer()

        val intent = Intent(context, ShiftTrackingService::class.java)
        context.startForegroundService(intent)
    }

    fun stopShift(context: Context) {
        // Log END
        logStatus(context, "SHIFT_END")

        val intent = Intent(context, ShiftTrackingService::class.java)
        context.stopService(intent)
        // Service onDestroy calls stopShiftInternal
    }

    // --- UPDATED: Toggle with Context ---
    fun toggleStatus(context: Context) {
        if (!_isTracking.value) return

        val newState = !_isBusy.value
        _isBusy.value = newState

        // Immediate Persistence
        val statusString = if (newState) "BUSY" else "SEARCHING"
        logStatus(context, statusString)
    }

    // --- Internal Helpers ---
    private fun logStatus(context: Context, status: String) {
        // Fire and forget DB write
        CoroutineScope(Dispatchers.IO).launch {
            val db = GigDatabase.getDatabase(context)
            db.gigDao().insertStatusLog(
                StatusLog(timestamp = System.currentTimeMillis(), status = status)
            )
        }
    }

    fun addMiles(miles: Double) {
        _milesDriven.value += miles
    }

    fun stopShiftInternal() {
        _isTracking.value = false
        _isBusy.value = false
        timerJob?.cancel()
    }

    fun restoreState(savedStartTime: Long, savedMiles: Double) {
        startTime = savedStartTime
        _milesDriven.value = savedMiles
        _isTracking.value = true
        startTimer()
    }

    fun getStartTime(): Long {
        return startTime
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_isTracking.value) {
                val now = System.currentTimeMillis()
                _durationSeconds.value = (now - startTime) / 1000
                delay(1000L)
            }
        }
    }
}