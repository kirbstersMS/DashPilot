package com.example.dashpilot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.dashpilot.data.GigDatabase
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyRecapScreen(navController: NavController, initialDateMillis: Long) {
    val context = LocalContext.current
    val db = remember { GigDatabase.getDatabase(context) }

    var currentWeekStart by remember { mutableLongStateOf(normalizeDateToLocal(initialDateMillis)) }

    val allEarnings by db.gigDao().getAllEarnings().collectAsState(initial = emptyList())
    val allExpenses by db.gigDao().getAllExpenses().collectAsState(initial = emptyList())
    val allShifts by db.gigDao().getAllShifts().collectAsState(initial = emptyList())

    val dailyStats = remember(currentWeekStart, allEarnings, allExpenses, allShifts) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentWeekStart

        val days = mutableListOf<DailySummary>()
        repeat(7) {
            val dayStart = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = calendar.timeInMillis

            // Filter data for this specific day
            val dayEarnings = allEarnings.filter { it.date in dayStart until dayEnd }

            val dEarn = dayEarnings.sumOf { it.amount }
            val dTrips = dayEarnings.sumOf { it.tripCount } // New: Sum trips
            val dExp = allExpenses.filter { it.date in dayStart until dayEnd }.sumOf { it.amount }
            val dHours = allShifts.filter { it.date in dayStart until dayEnd }.sumOf { it.durationHours }
            val dMiles = allShifts.filter { it.date in dayStart until dayEnd }.sumOf { it.miles }

            days.add(DailySummary(dayStart, dEarn, dExp, dHours, dMiles, dTrips))
        }
        days
    }

    // Calculate Weekly Aggregates
    val totalEarn = dailyStats.sumOf { it.earnings }
    val totalHours = dailyStats.sumOf { it.hours }
    val totalMiles = dailyStats.sumOf { it.miles }
    val totalTrips = dailyStats.sumOf { it.trips }

    // Calculate Efficiency Metrics (Avoid NaN with checks)
    val dollarsPerHour = if (totalHours > 0) totalEarn / totalHours else 0.0
    val dollarsPerMile = if (totalMiles > 0) totalEarn / totalMiles else 0.0
    val dollarsPerTrip = if (totalTrips > 0) totalEarn / totalTrips else 0.0
    val tripsPerHour = if (totalHours > 0) totalTrips.toDouble() / totalHours else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatWeekRange(currentWeekStart)) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { currentWeekStart -= 604800000L }) { Icon(Icons.Default.ChevronLeft, "Prev") }
                    IconButton(onClick = { currentWeekStart += 604800000L }) { Icon(Icons.Default.ChevronRight, "Next") }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            // UPDATED: Efficiency Stats Card
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceEvenly) {
                    StatCompact("$/Hr", "$${"%.2f".format(dollarsPerHour)}")
                    StatCompact("$/Mi", "$${"%.2f".format(dollarsPerMile)}")
                    StatCompact("$/Trip", "$${"%.2f".format(dollarsPerTrip)}")
                    StatCompact("Trips/Hr", "%.1f".format(tripsPerHour))
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dailyStats) { DailyRow(it) }
            }
        }
    }
}

// --- DATE FIX HELPER ---
fun normalizeDateToLocal(millis: Long): Long {
    val cal = Calendar.getInstance() // Local
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// UPDATED: Added 'trips' to data class
data class DailySummary(
    val date: Long,
    val earnings: Double,
    val expenses: Double,
    val hours: Double,
    val miles: Double,
    val trips: Int
)

@Composable
fun DailyRow(day: DailySummary) {
    // ... existing implementation ...
    // Note: ensure you copy the existing DailyRow implementation here if you aren't changing it
    // The previous implementation will work fine as long as it ignores the new 'trips' field.

    val isZero = day.earnings == 0.0 && day.expenses == 0.0 && day.miles == 0.0
    val textColor = if (isZero) Color.Gray else MaterialTheme.colorScheme.onSurface
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.width(60.dp)) {
                Text(SimpleDateFormat("EEE", Locale.getDefault()).format(Date(day.date)), fontWeight = FontWeight.Bold, color = textColor)
                Text(SimpleDateFormat("dd", Locale.getDefault()).format(Date(day.date)), style = MaterialTheme.typography.bodySmall, color = textColor)
            }
            if (!isZero) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // You could add stats here if you wanted, e.g. day.trips
                    if (day.hours > 0) Text("%.1fh".format(day.hours), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end=8.dp))
                    if (day.miles > 0) Text("%.0fmi".format(day.miles), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end=8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$${day.earnings.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (day.expenses > 0) Text("-$${day.expenses.toInt()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else { Text("-", color = Color.Gray) }
        }
    }
}

fun formatWeekRange(start: Long): String {
    val end = start + (6 * 24 * 3600 * 1000)
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return "${fmt.format(Date(start))} - ${fmt.format(Date(end))}"
}

@Composable
private fun StatCompact(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}