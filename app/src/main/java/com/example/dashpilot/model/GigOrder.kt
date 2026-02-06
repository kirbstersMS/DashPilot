package com.example.dashpilot.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class GigOrder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(), // Capture EXACTLY when we saw it
    val platform: String = "Unknown",
    val price: Double,
    val distanceMiles: Double,
    val durationMinutes: Int,
    val isEstimate: Boolean = false,
    val orderCount: Int = 1,
    val dropoffLocation: String = ""
) {
    // Computed properties are automatically ignored by Room if they don't have backing fields
    val dollarsPerMile: Double
        get() = if (distanceMiles > 0) price / distanceMiles else 0.0

    val dollarsPerHour: Double
        get() = if (durationMinutes > 0) (price / durationMinutes) * 60 else 0.0
}