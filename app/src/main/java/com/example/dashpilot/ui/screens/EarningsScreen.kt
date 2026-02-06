package com.example.dashpilot.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashpilot.data.Earning
import com.example.dashpilot.data.Expense
import com.example.dashpilot.data.GigDatabase
import com.example.dashpilot.data.Shift
import com.example.dashpilot.data.ShiftTracker
import com.example.dashpilot.ui.Screen
import com.example.dashpilot.ui.overlay.AppSwitcherController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class BottomSheetType { MENU, EARNINGS, OTHER, MILES, EXPENSE }

const val MILEAGE_RATE = 0.70

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsScreen(appSwitcherController: AppSwitcherController, navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GigDatabase.getDatabase(context) }

    val allEarnings by db.gigDao().getAllEarnings().collectAsState(initial = emptyList())
    val allExpenses by db.gigDao().getAllExpenses().collectAsState(initial = emptyList())
    val allShifts by db.gigDao().getAllShifts().collectAsState(initial = emptyList())

    val thisWeekStats = remember(allEarnings, allExpenses, allShifts) { calculateStats(allEarnings, allExpenses, allShifts, 0) }
    val lastWeekStats = remember(allEarnings, allExpenses, allShifts) { calculateStats(allEarnings, allExpenses, allShifts, 1) }
    val yearStats = remember(allEarnings, allExpenses, allShifts) { calculateStats(allEarnings, allExpenses, allShifts, -1) }

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var showStopDialog by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<BottomSheetType?>(null) }
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var onDateSelected by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    fun getWeekStart(offset: Int): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.add(Calendar.WEEK_OF_YEAR, -offset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    var showDeductionInfo by remember { mutableStateOf(false) }

    BackHandler(enabled = activeSheet != null) { activeSheet = null }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Earnings", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = { appSwitcherController.toggle() }) { Icon(Icons.AutoMirrored.Outlined.ViewSidebar, "Sidebar") }
                        IconButton(onClick = { activeSheet = BottomSheetType.MENU }) { Icon(Icons.Default.AddCircle, "Add") }
                    }
                }
            }
            item { LiveShiftMonitor(context = context) { showStopDialog = true } }
            item {
                SectionHeader("This Week", onClick = { navController.navigate("weekly_recap/${getWeekStart(0)}") })
                SummaryCard(thisWeekStats)
            }
            item {
                SectionHeader("Last Week", onClick = { navController.navigate("weekly_recap/${getWeekStart(1)}") })
                SummaryCard(lastWeekStats)
            }
            item {
                SectionHeader(
                    text = currentYear.toString(),
                    onClick = { navController.navigate(Screen.YearlyRecap.createRoute(currentYear)) }
                )
                YearlyCard(
                    stats = yearStats,
                    onAllYearsClick = { navController.navigate(Screen.AllYears.route) },
                    onByPurposeClick = { navController.navigate(Screen.ByPurpose.route) },
                    onDeductionInfoClick = { showDeductionInfo = true }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        if (activeSheet != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { activeSheet = null })
            AnimatedVisibility(visible = true, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}) {
                    Box(Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                        when (activeSheet) {
                            BottomSheetType.MENU -> MenuContent(
                                onEarnings = { activeSheet = BottomSheetType.EARNINGS },
                                onMiles = { activeSheet = BottomSheetType.MILES },
                                onExpense = { activeSheet = BottomSheetType.EXPENSE },
                                onOther = { activeSheet = BottomSheetType.OTHER }
                            )
                            BottomSheetType.EARNINGS -> EarningsInputContent(db, "New Earnings", { cb -> onDateSelected = cb; showDatePicker = true }) { activeSheet = null }
                            BottomSheetType.OTHER -> TipsInputContent(db, { cb -> onDateSelected = cb; showDatePicker = true }) { activeSheet = null }
                            BottomSheetType.MILES -> MileageInputContent(db, { cb -> onDateSelected = cb; showDatePicker = true }) { activeSheet = null }
                            BottomSheetType.EXPENSE -> ExpenseInputContent(context, db, { cb -> onDateSelected = cb; showDatePicker = true }) { activeSheet = null }
                            null -> {}
                        }
                    }
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("End Shift?") },
            text = { Text("Save this shift's data to your history?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val finalSeconds = ShiftTracker.durationSeconds.value
                        val finalMiles = ShiftTracker.milesDriven.value
                        // FIX: Removed 'deduction' argument
                        db.gigDao().insertShift(Shift(
                            date = System.currentTimeMillis(),
                            durationHours = finalSeconds / 3600.0,
                            miles = finalMiles
                        ))
                        ShiftTracker.stopShift(context)
                        showStopDialog = false
                    }
                }) { Text("Save Shift") }
            },
            dismissButton = {
                Row { TextButton(onClick = { ShiftTracker.stopShift(context); showStopDialog = false }) { Text("Discard") }; TextButton(onClick = { showStopDialog = false }) { Text("Cancel") } }
            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val utcMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val localMillis = convertUtcToLocal(utcMillis)
                    showDatePicker = false
                    onDateSelected?.invoke(localMillis)
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showDeductionInfo) {
        AlertDialog(
            onDismissRequest = { showDeductionInfo = false },
            icon = { Icon(Icons.Outlined.Info, null) },
            title = { Text("Standard Mileage Deduction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GigPilot uses the Standard Mileage Rate (currently $${"%.2f".format(MILEAGE_RATE)}/mile) to calculate your estimated tax deduction.")
                    HorizontalDivider()
                    Text("This rate is designed by the IRS to cover:", style = MaterialTheme.typography.titleSmall)
                    Text("• Gas & Oil\n• Repairs & Maintenance\n• Insurance\n• Depreciation", style = MaterialTheme.typography.bodyMedium)
                    Text("Note: You generally cannot deduct actual expenses (like gas receipts) AND use the mileage deduction simultaneously for the same vehicle.", style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeductionInfo = false }) { Text("Got it") }
            }
        )
    }
}

private fun convertUtcToLocal(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = utcMillis

    val localCal = Calendar.getInstance()
    localCal.set(
        utcCal.get(Calendar.YEAR),
        utcCal.get(Calendar.MONTH),
        utcCal.get(Calendar.DAY_OF_MONTH),
        0, 0, 0
    )
    return localCal.timeInMillis
}

fun formatDateOnly(millis: Long): String = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()).format(Date(millis))

@Composable
fun TipsInputContent(db: GigDatabase, requestDate: ((Long) -> Unit) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var amount by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tips / Other", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        OutlinedButton(onClick = { requestDate { dateMillis = it } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(12.dp)); Text(formatDateOnly(dateMillis), color = MaterialTheme.colorScheme.onSurface)
        }
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                keyboardController?.hide()
                if (amount.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        db.gigDao().insertEarning(Earning(0, dateMillis, "Tips", amount.toDouble(), 0))
                        withContext(Dispatchers.Main) { onClose() }
                    }
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Save Tips") }
    }
}

@Composable
fun EarningsInputContent(db: GigDatabase, title: String, requestDate: ((Long) -> Unit) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var ddAmount by remember { mutableStateOf("") }; var ddTrips by remember { mutableStateOf("") }
    var uberAmount by remember { mutableStateOf("") }; var uberTrips by remember { mutableStateOf("") }
    var ghAmount by remember { mutableStateOf("") }; var ghTrips by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        OutlinedButton(onClick = { requestDate { dateMillis = it } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(12.dp)); Text(formatDateOnly(dateMillis), color = MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Platform", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Amount ($)", Modifier.width(100.dp), fontWeight = FontWeight.Bold)
            Text("Trips (#)", Modifier.width(70.dp), fontWeight = FontWeight.Bold)
        }
        PlatformRow("DoorDash", Color(0xFFFF3008), ddAmount, ddTrips, { ddAmount = it }, { ddTrips = it })
        PlatformRow("Uber Eats", Color(0xFF06C167), uberAmount, uberTrips, { uberAmount = it }, { uberTrips = it })
        PlatformRow("Grubhub", Color(0xFFFF8000), ghAmount, ghTrips, { ghAmount = it }, { ghTrips = it })
        Button(
            onClick = {
                keyboardController?.hide()
                scope.launch(Dispatchers.IO) {
                    if (ddAmount.isNotEmpty()) db.gigDao().insertEarning(Earning(0, dateMillis, "DoorDash", ddAmount.toDouble(), ddTrips.toIntOrNull() ?: 1))
                    if (uberAmount.isNotEmpty()) db.gigDao().insertEarning(Earning(0, dateMillis, "Uber Eats", uberAmount.toDouble(), uberTrips.toIntOrNull() ?: 1))
                    if (ghAmount.isNotEmpty()) db.gigDao().insertEarning(Earning(0, dateMillis, "Grubhub", ghAmount.toDouble(), ghTrips.toIntOrNull() ?: 1))
                    withContext(Dispatchers.Main) { onClose() }
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Save") }
    }
}

@Composable
fun PlatformRow(name: String, color: Color, amt: String, trips: String, onAmt: (String) -> Unit, onTrips: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp))); Spacer(Modifier.width(8.dp))
        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(value = amt, onValueChange = onAmt, placeholder = { Text("$") }, modifier = Modifier.width(100.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(value = trips, onValueChange = onTrips, placeholder = { Text("#") }, modifier = Modifier.width(70.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
    }
}

@Composable
private fun SummaryCard(stats: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Earnings", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stats[3], style = MaterialTheme.typography.bodySmall); Text(" • "); Text(stats[0], fontWeight = FontWeight.Bold) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Expenses", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stats[1])
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Deduction", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stats[4], style = MaterialTheme.typography.bodySmall); Text(" • "); Text(stats[2]) }
            }
        }
    }
}

@Composable
fun MileageInputContent(db: GigDatabase, requestDate: ((Long) -> Unit) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var miles by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var purpose by remember { mutableStateOf("Business") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("New Mileage", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        OutlinedButton(onClick = { requestDate { dateMillis = it } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(12.dp)); Text(formatDateOnly(dateMillis), color = MaterialTheme.colorScheme.onSurface)
        }
        OutlinedTextField(value = miles, onValueChange = { miles = it }, label = { Text("Mileage (mi)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Hours Worked") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = purpose, onValueChange = {}, readOnly = true, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                keyboardController?.hide()
                val mi = miles.toDoubleOrNull() ?: 0.0
                val hr = hours.toDoubleOrNull() ?: 0.0
                if (mi <= 0) return@Button
                scope.launch(Dispatchers.IO) {
                    // FIX: Removed 'deduction' argument
                    db.gigDao().insertShift(Shift(0, dateMillis, hr, mi, purpose))
                    withContext(Dispatchers.Main) { onClose() }
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Save Mileage") }
    }
}

@Composable
fun ExpenseInputContent(context: Context, db: GigDatabase, requestDate: ((Long) -> Unit) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Gas") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> receiptUri = uri }
    var showCategoryMenu by remember { mutableStateOf(false) }
    val categories = listOf("Gas", "Car payment", "Insurance", "Maintenance", "Phone", "Supplies", "Food", "Other")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("New Expense", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        OutlinedButton(onClick = { requestDate { dateMillis = it } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(12.dp)); Text(formatDateOnly(dateMillis), color = MaterialTheme.colorScheme.onSurface)
        }
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, modifier = Modifier.fillMaxWidth().clickable { showCategoryMenu = true })
            Box(Modifier.matchParentSize().clickable { showCategoryMenu = true })
            DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                categories.forEach { cat -> DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; showCategoryMenu = false }) }
            }
        }
        Text("Receipt", style = MaterialTheme.typography.labelMedium)
        Box(modifier = Modifier.fillMaxWidth().height(80.dp).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxSize(), border = BorderStroke(1.dp, if (receiptUri != null) Color(0xFF4CAF50) else Color.Gray)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (receiptUri != null) Icons.Outlined.CheckCircle else Icons.Outlined.PhotoCamera, null, tint = if (receiptUri != null) Color(0xFF4CAF50) else Color.Gray)
                    Text(if (receiptUri != null) "Photo Selected" else "Add photo", color = if (receiptUri != null) Color(0xFF4CAF50) else Color.Gray)
                }
            }
        }
        Button(
            onClick = {
                keyboardController?.hide()
                if (amount.isEmpty()) return@Button
                scope.launch(Dispatchers.IO) {
                    // FEATURE: We save the file to local storage, but we DO NOT link it in DB (to avoid CSV issues)
                    receiptUri?.let { uri ->
                        try {
                            val file = File(context.filesDir, "receipt_${System.currentTimeMillis()}.jpg")
                            context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
                            // We ignore 'file.absolutePath' here
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    // FIX: Removed 'receiptPath' argument from Expense constructor
                    db.gigDao().insertExpense(Expense(0, dateMillis, category, amount.toDouble()))
                    withContext(Dispatchers.Main) { onClose() }
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Save Expense") }
    }
}

@Composable
private fun SectionHeader(text: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun calculateStats(earns: List<Earning>, exps: List<Expense>, shifts: List<Shift>, weekOffset: Int): List<String> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    val startRange: Long
    val endRange: Long

    if (weekOffset == -1) {
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        startRange = calendar.timeInMillis
        calendar.add(Calendar.YEAR, 1)
        endRange = calendar.timeInMillis
    } else {
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.add(Calendar.WEEK_OF_YEAR, -weekOffset)
        startRange = calendar.timeInMillis
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        endRange = calendar.timeInMillis
    }

    val fEarn = earns.filter { it.date in startRange until endRange }
    val fExp = exps.filter { it.date in startRange until endRange }
    val fShift = shifts.filter { it.date in startRange until endRange }

    return listOf(
        "$${"%.0f".format(fEarn.sumOf { it.amount })}",
        "$${"%.0f".format(fExp.sumOf { it.amount })}",
        "$${"%.0f".format(fShift.sumOf { it.miles * MILEAGE_RATE })}",
        "%.1f hrs".format(fShift.sumOf { it.durationHours }),
        "%.1f mi".format(fShift.sumOf { it.miles })
    )
}

@Composable
fun MenuContent(onEarnings: () -> Unit, onMiles: () -> Unit, onExpense: () -> Unit, onOther: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
            AddGridItem(Icons.Default.AddCircle, Color(0xFF4CAF50), "Gig Earnings", Modifier.weight(1f), onEarnings)
            AddGridItem(Icons.Outlined.Schedule, MaterialTheme.colorScheme.onSurface, "Miles", Modifier.weight(1f), onMiles)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
            AddGridItem(Icons.Default.RemoveCircle, Color(0xFFEF5350), "Expenses", Modifier.weight(1f), onExpense)
            AddGridItem(Icons.Outlined.Receipt, MaterialTheme.colorScheme.onSurface, "Other / Tips", Modifier.weight(1f), onOther)
        }
    }
}
@Composable
private fun AddGridItem(icon: ImageVector, iconColor: Color, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = modifier.height(100.dp).clickable { onClick() }) {
        Column(Modifier.padding(12.dp).fillMaxSize(), Arrangement.SpaceBetween, Alignment.Start) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(28.dp)); Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable
private fun YearlyCard(
    stats: List<String>,
    onAllYearsClick: () -> Unit,
    onByPurposeClick: () -> Unit,
    onDeductionInfoClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column { Text(stats[0], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Earnings", style = MaterialTheme.typography.bodySmall) }
                Column { Text(stats[1], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Expenses", style = MaterialTheme.typography.bodySmall) }
                Column { Text(stats[2], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Deduction", style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            ListItemArrow("All Years", onAllYearsClick)
            ListItemArrow("By Purpose", onByPurposeClick)
            ListItemArrow("About Mileage Deduction", onDeductionInfoClick)
        }
    }
}
@Composable
private fun ListItemArrow(text: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(text); Icon(Icons.Default.ChevronRight, null) } }
@Composable
fun LiveShiftMonitor(context: Context, onStopRequest: () -> Unit) {
    val isTracking by ShiftTracker.isTracking.collectAsState()
    val liveMiles by ShiftTracker.milesDriven.collectAsState()
    val liveSeconds by ShiftTracker.durationSeconds.collectAsState()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Track Miles", style = MaterialTheme.typography.titleMedium); Switch(checked = isTracking, onCheckedChange = { if (isTracking) onStopRequest() else ShiftTracker.startShift(context) }) }
            if (isTracking) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Column { Text("TIME", style = MaterialTheme.typography.labelSmall); Text("%02d:%02d:%02d".format(liveSeconds/3600, (liveSeconds%3600)/60, liveSeconds%60), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    Column { Text("MILES", style = MaterialTheme.typography.labelSmall); Text("%.1f".format(liveMiles), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}