package com.example.dashpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.dashpilot.data.Earning
import com.example.dashpilot.data.GigDatabase
import com.example.dashpilot.data.Shift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Sealed Interface to mix Earnings and Shifts in one list
sealed interface HistoryItem {
    val date: Long
    data class EarnItem(val data: Earning) : HistoryItem { override val date = data.date }
    data class ShiftItem(val data: Shift) : HistoryItem { override val date = data.date }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(navController: NavController, serviceName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GigDatabase.getDatabase(context) }

    // 1. Fetch Both Data Sources
    val allEarnings by db.gigDao().getAllEarnings().collectAsState(initial = emptyList())
    val allShifts by db.gigDao().getAllShifts().collectAsState(initial = emptyList())

    // 2. Filter & Merge
    val combinedHistory = remember(allEarnings, allShifts, serviceName) {
        val e = allEarnings.filter { it.platform.equals(serviceName, ignoreCase = true) }
            .map { HistoryItem.EarnItem(it) }
        val s = allShifts.filter { it.purpose.equals(serviceName, ignoreCase = true) }
            .map { HistoryItem.ShiftItem(it) }

        (e + s).sortedByDescending { it.date }
    }

    // 3. Calculate Stats
    val totalEarned = combinedHistory.filterIsInstance<HistoryItem.EarnItem>().sumOf { it.data.amount }
    val totalTrips = combinedHistory.filterIsInstance<HistoryItem.EarnItem>().sumOf { it.data.tripCount }
    val totalMiles = combinedHistory.filterIsInstance<HistoryItem.ShiftItem>().sumOf { it.data.miles }
    val totalHours = combinedHistory.filterIsInstance<HistoryItem.ShiftItem>().sumOf { it.data.durationHours }

    val avgPerTrip = if (totalTrips > 0) totalEarned / totalTrips else 0.0
    val isTip = serviceName.equals("Tips", ignoreCase = true)

    // 4. Edit States
    var editingEarning by remember { mutableStateOf<Earning?>(null) }
    var editingShift by remember { mutableStateOf<Shift?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(serviceName) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            // --- DYNAMIC STATS HEADER ---
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceEvenly) {
                    // Always show Earnings
                    StatCompact("Total", "$${totalEarned.toInt()}")

                    // Show Trips only if relevant
                    if (totalTrips > 0 && !isTip) StatCompact("Trips", "$totalTrips")

                    // Show Miles/Hours if relevant
                    if (totalMiles > 0) StatCompact("Miles", "%.1f".format(totalMiles))
                    if (totalHours > 0) StatCompact("Hours", "%.1f".format(totalHours))

                    // Fallback Avg if space permits and no miles
                    if (totalTrips > 0 && totalMiles == 0.0) StatCompact("Avg/Trip", "$${"%.2f".format(avgPerTrip)}")
                }
            }

            Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            if (combinedHistory.isEmpty()) {
                Text("No records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(combinedHistory) { item ->
                    when (item) {
                        is HistoryItem.EarnItem -> {
                            EarningRow(item.data, isTip) { editingEarning = item.data }
                        }
                        is HistoryItem.ShiftItem -> {
                            ShiftRow(item.data) { editingShift = item.data }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---
    if (editingEarning != null) {
        EditEarningDialog(
            earning = editingEarning!!,
            isTip = isTip,
            onDismiss = { },
            onSave = { updated ->
                scope.launch(Dispatchers.IO) {
                    db.gigDao().updateEarning(updated)
                    withContext(Dispatchers.Main) { }
                }
            }
        )
    }

    if (editingShift != null) {
        EditShiftDialog(
            shift = editingShift!!,
            onDismiss = { },
            onSave = { updated ->
                scope.launch(Dispatchers.IO) {
                    db.gigDao().updateShift(updated)
                    withContext(Dispatchers.Main) { }
                }
            }
        )
    }
}

// --- ROW COMPOSABLES ---

@Composable
fun EarningRow(item: Earning, isTip: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Payments, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(SimpleDateFormat("MMM dd", Locale.US).format(Date(item.date)), fontWeight = FontWeight.SemiBold)
                    if (item.tripCount > 0 && !isTip) {
                        Text("${item.tripCount} trips", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$${item.amount}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50)) // Green Text
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun ShiftRow(item: Shift, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(SimpleDateFormat("MMM dd", Locale.US).format(Date(item.date)), fontWeight = FontWeight.SemiBold)
                    Text("%.1f hrs".format(item.durationHours), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%.1f mi".format(item.miles), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// --- DIALOGS IMPLEMENTATION ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShiftDialog(shift: Shift, onDismiss: () -> Unit, onSave: (Shift) -> Unit) {
    var miles by remember { mutableStateOf(shift.miles.toString()) }
    var hours by remember { mutableStateOf(shift.durationHours.toString()) }
    var dateMillis by remember { mutableLongStateOf(shift.date) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Shift") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                    Text(SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()).format(Date(dateMillis)))
                }
                OutlinedTextField(value = miles, onValueChange = { miles = it }, label = { Text("Miles") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Hours") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val newMiles = miles.toDoubleOrNull() ?: 0.0
                val newHours = hours.toDoubleOrNull() ?: 0.0
                // VIBE FIX: Removed explicit deduction calculation here (it's dynamic now)
                onSave(shift.copy(date = dateMillis, miles = newMiles, durationHours = newHours))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { },
            confirmButton = { TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { utc ->
                    val cal = Calendar.getInstance(); val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")); utcCal.timeInMillis = utc
                    cal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0,0,0)
                    dateMillis = cal.timeInMillis
                }
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun StatCompact(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEarningDialog(earning: Earning, isTip: Boolean, onDismiss: () -> Unit, onSave: (Earning) -> Unit) {
    var amount by remember { mutableStateOf(earning.amount.toString()) }
    var trips by remember { mutableStateOf(earning.tripCount.toString()) }
    var dateMillis by remember { mutableLongStateOf(earning.date) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                    Text(SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()).format(Date(dateMillis)))
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (!isTip) OutlinedTextField(value = trips, onValueChange = { trips = it }, label = { Text("Trips (#)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val newAmount = amount.toDoubleOrNull() ?: 0.0
                val newTrips = trips.toIntOrNull() ?: 1
                onSave(earning.copy(date = dateMillis, amount = newAmount, tripCount = newTrips))
            }) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { },
            confirmButton = { TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { utc ->
                    val cal = Calendar.getInstance(); val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")); utcCal.timeInMillis = utc
                    cal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0,0,0)
                    dateMillis = cal.timeInMillis
                }
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}