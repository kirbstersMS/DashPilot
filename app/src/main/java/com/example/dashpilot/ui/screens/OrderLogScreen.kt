package com.example.dashpilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.dashpilot.data.GigDatabase
import com.example.dashpilot.model.GigOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderLogScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GigDatabase.getDatabase(context) }

    // Performance: Hoist the formatter once
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // State: Date Filtering
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)

    // State: Pagination & Data
    var dailyOrders by remember { mutableStateOf<List<GigOrder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) } // General loading
    var isAppending by remember { mutableStateOf(false) } // Loading MORE at bottom
    var endReached by remember { mutableStateOf(false) } // No more data to load
    var currentOffset by remember { mutableIntStateOf(0) }

    val pageSize = 20

    // Logic: Reset and Load First Page when date changes
    LaunchedEffect(selectedDateMillis) {
        isLoading = true
        dailyOrders = emptyList() // Clear list immediately to avoid confusion
        currentOffset = 0
        endReached = false

        // Heavy lifting on IO thread
        val newOrders = withContext(Dispatchers.IO) {
            val (start, end) = getDayRange(selectedDateMillis)
            db.gigDao().getPagedOrders(start, end, limit = pageSize, offset = 0)
        }

        dailyOrders = newOrders
        if (newOrders.size < pageSize) endReached = true
        isLoading = false
    }

    // Function: Load Next Page (Called when scrolling)
    fun loadNextPage() {
        if (isAppending || endReached) return

        isAppending = true
        scope.launch(Dispatchers.IO) {
            val (start, end) = getDayRange(selectedDateMillis)
            val nextOffset = currentOffset + pageSize

            val nextBatch = db.gigDao().getPagedOrders(start, end, limit = pageSize, offset = nextOffset)

            withContext(Dispatchers.Main) {
                if (nextBatch.isEmpty()) {
                    endReached = true
                } else {
                    dailyOrders = dailyOrders + nextBatch
                    currentOffset = nextOffset
                    if (nextBatch.size < pageSize) endReached = true
                }
                isAppending = false
            }
        }
    }

    // State: Deletion (Simplified)
    var orderToDelete by remember { mutableStateOf<GigOrder?>(null) }

    val displayDate = remember(selectedDateMillis) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(selectedDateMillis))
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // --- Header Row (Same as before) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text("Daily Log", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { showDatePicker = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(displayDate)
            }
        }

        // --- List Content ---
        if (isLoading) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (dailyOrders.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No orders recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    count = dailyOrders.size,
                    key = { index -> dailyOrders[index].id },
                    contentType = { "OrderCard" } // optimization: helps compose reuse layouts
                ) { index ->
                    val order = dailyOrders[index]

                    // --- Pagination Trigger ---
                    if (index >= dailyOrders.lastIndex && !endReached && !isAppending) {
                        LaunchedEffect(Unit) { loadNextPage() }
                    }

                    // --- PRE-CALCULATION (The Magic) ---
                    // We calculate these once per item lifecycle, so the Card just receives static data.
                    val formattedTime = remember(order.timestamp) {
                        timeFormatter.format(Date(order.timestamp))
                    }

                    val platformColor = remember(order.platform) {
                        getPlatformColor(order.platform)
                    }

                    val dollarPerMile = remember(order.price, order.distanceMiles) {
                        if (order.distanceMiles > 0) order.price / order.distanceMiles else 0.0
                    }

                    LedgerOrderCard(
                        order = order,
                        formattedTime = formattedTime,
                        platformColor = platformColor,
                        dollarPerMile = dollarPerMile,
                        onDeleteClick = { orderToDelete = order }
                    )
                }

                // Show a small spinner at bottom if we are fetching more
                if (isAppending) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // --- Dialogs (DatePicker & Delete) ---
    // (Keep your existing Dialog code here, it's fine)
    // Note: For deletion, you now need to manually remove the item from 'dailyOrders' list
    // to avoid reloading the whole page and resetting the scroll position.

    if (orderToDelete != null) {
        AlertDialog(
            onDismissRequest = { orderToDelete = null },
            title = { Text("Delete Entry?") },
            text = { Text("Permanently delete this order?") },
            confirmButton = {
                Button(
                    onClick = {
                        val itemToRemove = orderToDelete // Capture for local removal
                        scope.launch(Dispatchers.IO) {
                            itemToRemove?.let { db.gigDao().deleteOrder(it) }
                            withContext(Dispatchers.Main) {
                                // Locally remove to prevent full reload lag
                                dailyOrders = dailyOrders.filter { it.id != itemToRemove?.id }
                                orderToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { orderToDelete = null }) { Text("Cancel") } }
        )
    }

    // (Include DatePicker dialog here...)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        // Use your helper function here
                        selectedDateMillis = convertUtcToLocal(it)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun LedgerOrderCard(
    order: GigOrder,
    formattedTime: String,      // Stable String
    platformColor: Color,       // Stable Color
    dollarPerMile: Double,      // Stable Double
    onDeleteClick: () -> Unit   // Stable Lambda
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

            // --- Row 1: Header ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Platform Chip
                Surface(
                    color = platformColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(platformColor, MaterialTheme.shapes.small))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = order.platform,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = platformColor
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = "$${String.format(Locale.US, "%.2f", order.price)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp).padding(start = 8.dp)) {
                    Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Row 2: Location ---
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Place, "Location", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).offset(y = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = order.dropoffLocation.ifBlank { "Location not recorded" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // --- Row 3: Metrics ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time (Passed in)
                Text(text = formattedTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (order.durationMinutes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${order.durationMinutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(text = "${order.distanceMiles} mi", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

                if (order.orderCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${order.orderCount} orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    }
                }

                // Dollar Per Mile (Passed in)
                Text(
                    text = "$${String.format(Locale.US, "%.2f", dollarPerMile)}/mi",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dollarPerMile >= 2.0) Color(0xFF4CAF50) else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// 5. OPTIMIZATION: Top-level function, no allocation per frame
private fun getPlatformColor(platformName: String): Color {
    return when(platformName.lowercase()) { // String comparison is fast, allocation minimized
        "doordash" -> Color(0xFFFF3008)
        "uber eats" -> Color(0xFF06C167)
        "grubhub" -> Color(0xFFFF8000)
        else -> Color(0xFF6750A4) // Fallback Primary
    }
}

// --- Helpers ---
private fun getDayRange(dateMillis: Long): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dateMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val end = cal.timeInMillis - 1
    return start to end
}

// Helper to fix the DatePicker UTC offset issue
private fun convertUtcToLocal(utcMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = utcMillis
    val zoneOffset = calendar.timeZone.getOffset(utcMillis)
    return utcMillis - zoneOffset
}