package com.example.dashpilot.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile // Ensure you have this dependency
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.dashpilot.data.GigDatabase
import com.example.dashpilot.data.UnifiedCsv
import com.example.dashpilot.data.GigPrefs // Assuming this exists based on your previous code
import com.example.dashpilot.util.GigLogger // Assuming this exists based on your previous code
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DeleteTarget { TAX_DATA, ORDER_HISTORY }
private enum class DataCategory { MASTER_LEDGER, ORDER_LOGS }

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onViewLogs: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val db = remember { GigDatabase.getDatabase(context) }

    // UI State
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var activeDataCategory by remember { mutableStateOf<DataCategory?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // --- PERMISSION CHECKS ---
    fun checkBgLocation() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isBatteryIgnored by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    var isAccessibilityOn by remember { mutableStateOf(checkAccessibilityService(context)) }
    var hasBackgroundLocation by remember { mutableStateOf(checkBgLocation()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
                isBatteryIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)
                isAccessibilityOn = checkAccessibilityService(context)
                hasBackgroundLocation = checkBgLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- LAUNCHERS ---

    // 1. Master Ledger (Single File CSV)
    val exportMasterLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(it)?.use { stream -> UnifiedCsv.exportToStream(db, stream) }
                withContext(Dispatchers.Main) { Toast.makeText(context, "Master Ledger Exported", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val importMasterLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val msg = context.contentResolver.openInputStream(it)?.use { stream -> UnifiedCsv.importFromStream(db, stream) } ?: "Error opening file"
                withContext(Dispatchers.Main) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            }
        }
    }

    // 2. Order & Status Logs (Folder Export / Single File Import)
    // EXPORT: Pick a folder -> Save 2 files
    val exportOrdersFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { treeUri ->
            scope.launch(Dispatchers.IO) {
                val directory = DocumentFile.fromTreeUri(context, treeUri)

                // Create File 1: Orders
                val orderFile = directory?.createFile("text/csv", "GigPilot_Orders.csv")
                orderFile?.uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri)?.use { out -> UnifiedCsv.exportOrdersToStream(db, out) }
                }

                // Create File 2: Status Logs
                val statusFile = directory?.createFile("text/csv", "GigPilot_StatusLogs.csv")
                statusFile?.uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri)?.use { out -> UnifiedCsv.exportStatusLogsToStream(db, out) }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved Orders & Status logs to folder", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // IMPORT: Pick a file (Orders only for now)
    val importOrdersLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val msg = context.contentResolver.openInputStream(it)?.use { stream -> UnifiedCsv.importOrdersFromStream(db, stream) } ?: "Error opening file"
                withContext(Dispatchers.Main) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            }
        }
    }

    // Debug Log Launcher
    val exportLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val logFile = java.io.File(context.filesDir, "gig_pilot_debug.log")
                if (logFile.exists()) {
                    context.contentResolver.openOutputStream(it)?.use { output -> logFile.inputStream().use { input -> input.copyTo(output) } }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Debug Logs Exported", Toast.LENGTH_SHORT).show() }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "No debug log found", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    // --- UI CONTENT ---
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold) }

        // APPEARANCE
        item {
            Text("Appearance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BrightnessMedium, null)
                    Spacer(Modifier.width(16.dp))
                    Text("Dark Mode", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
                }
            }
        }

        // SYSTEM CHECKS
        item {
            Text("System Checks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column {
                    PermissionRow("Overlay", hasOverlay, Icons.Outlined.Layers) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                    PermissionRow("Accessibility", isAccessibilityOn, Icons.Outlined.AccessibilityNew) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                    PermissionRow("Battery Opt.", isBatteryIgnored, Icons.Outlined.BatteryAlert) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = "package:${context.packageName}".toUri() }
                        try { context.startActivity(intent) } catch (_: Exception) { Toast.makeText(context, "Open battery settings manually", Toast.LENGTH_SHORT).show() }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                    // [RESTORED] Background Location
                    PermissionRow("Bg Location", hasBackgroundLocation, Icons.Outlined.LocationOn) {
                        // Android 11+ requires users to select "Allow all the time" in settings manually
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Select 'Allow all the time' in Permissions", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // --- DATA MANAGEMENT (Consolidated to 3 Buttons) ---
        item {
            Text("Data Management", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column {
                    // Button 1: Master Ledger
                    SettingsActionItem(
                        title = "Master Ledger",
                        subtitle = "Import or Export earnings & miles",
                        icon = Icons.Outlined.AccountBalance
                    ) {
                        activeDataCategory = DataCategory.MASTER_LEDGER
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                    // Button 2: Logs
                    SettingsActionItem(
                        title = "Order & Status Logs",
                        subtitle = "Backup history & app status",
                        icon = Icons.Outlined.HistoryEdu
                    ) {
                        activeDataCategory = DataCategory.ORDER_LOGS
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                    // Button 3: Viewer
                    SettingsActionItem(
                        title = "View Raw Log",
                        subtitle = "Browse database entries directly",
                        icon = Icons.AutoMirrored.Outlined.ListAlt
                    ) {
                        onViewLogs()
                    }
                }
            }
        }

        // DANGER ZONE
        item {
            Spacer(Modifier.height(16.dp))
            Text("Danger Zone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column {
                    SettingsActionItem(
                        title = "Wipe Tax Data",
                        subtitle = "Clears Earnings, Miles, & Expenses",
                        icon = Icons.Outlined.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error
                    ) { deleteTarget = DeleteTarget.TAX_DATA }
                    HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    SettingsActionItem(
                        title = "Wipe Order History",
                        subtitle = "Clears scraped/manual order logs",
                        icon = Icons.Outlined.History,
                        iconTint = MaterialTheme.colorScheme.error
                    ) { deleteTarget = DeleteTarget.ORDER_HISTORY }
                }
            }
        }

        // ADVANCED
        item {
            Spacer(Modifier.height(16.dp))
            Text("Advanced", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column {
                    val prefs = remember { GigPrefs(context) }
                    var isDebugEnabled by remember { mutableStateOf(prefs.isDebugLoggingEnabled) }
                    Row(modifier = Modifier.fillMaxWidth().clickable { val newState = !isDebugEnabled; isDebugEnabled = newState; prefs.isDebugLoggingEnabled = newState; Toast.makeText(context, if(newState) "Debug Logging ON" else "Debug Logging OFF", Toast.LENGTH_SHORT).show() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BugReport, null, tint = if (isDebugEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) { Text("Scraper Debug Logging", fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text("Dumps screen content to log file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(checked = isDebugEnabled, onCheckedChange = { isDebugEnabled = it; prefs.isDebugLoggingEnabled = it })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    SettingsActionItem("Export Debug Logs", "Save forensic logs to file", Icons.Outlined.SaveAlt) {
                        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
                        exportLogLauncher.launch("GigPilot_Debug_$timeStamp.txt")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    SettingsActionItem("Clear Log History", "Wipe the current log file clean", Icons.Outlined.Delete, iconTint = MaterialTheme.colorScheme.error) {
                        GigLogger.clear(); Toast.makeText(context, "Logs Cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    // --- BOTTOM SHEET FOR DATA ACTIONS ---
    if (activeDataCategory != null) {
        ModalBottomSheet(
            onDismissRequest = { activeDataCategory = null },
            sheetState = sheetState
        ) {
            val category = activeDataCategory!!
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 16.dp, end = 16.dp)) {
                Text(
                    text = if (category == DataCategory.MASTER_LEDGER) "Master Ledger" else "Order & Status Logs",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // EXPORT
                ListItem(
                    headlineContent = { Text("Export Data") },
                    supportingContent = {
                        Text(if(category == DataCategory.ORDER_LOGS) "Select a FOLDER to save Order & Status logs" else "Save Ledger to CSV")
                    },
                    leadingContent = { Icon(Icons.Outlined.FileUpload, null) },
                    modifier = Modifier.clickable {
                        if (category == DataCategory.MASTER_LEDGER) {
                            exportMasterLauncher.launch("GigPilot_Master_Ledger.csv")
                        } else {
                            // Launches folder picker
                            exportOrdersFolderLauncher.launch(null)
                        }
                        activeDataCategory = null
                    }
                )

                // IMPORT
                ListItem(
                    headlineContent = { Text("Import Data") },
                    supportingContent = { Text("Select a compatible CSV file") },
                    leadingContent = { Icon(Icons.Outlined.FileDownload, null) },
                    modifier = Modifier.clickable {
                        if (category == DataCategory.MASTER_LEDGER) {
                            // 🟢 FIX: Allow any file type so nothing is grayed out.
                            // Your UnifiedCsv.import... logic will just fail gracefully if they pick a photo.
                            importMasterLauncher.launch(arrayOf("*/*"))
                        } else {
                            importOrdersLauncher.launch(arrayOf("*/*"))
                        }
                        activeDataCategory = null
                    }
                )
            }
        }
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (deleteTarget != null) {
        var deleteConfirmationInput by remember { mutableStateOf("") }
        val (title, body) = when (deleteTarget!!) {
            DeleteTarget.TAX_DATA -> "Wipe Tax Data?" to "Permanently delete earnings, shifts, and expenses. Order History remains."
            DeleteTarget.ORDER_HISTORY -> "Wipe Order History?" to "Permanently delete logged gig orders. Tax Data remains."
        }
        AlertDialog(
            // 🟢 Fix 1: Removed redundant 'deleteConfirmationInput = ""'
            onDismissRequest = { deleteTarget = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(body)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteConfirmationInput,
                        onValueChange = { deleteConfirmationInput = it },
                        label = { Text("Type DELETE to confirm") },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.error,
                            cursorColor = MaterialTheme.colorScheme.error,
                            focusedLabelColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            titleContentColor = MaterialTheme.colorScheme.error,
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            when (deleteTarget) {
                                DeleteTarget.TAX_DATA -> { db.gigDao().clearEarnings(); db.gigDao().clearShifts(); db.gigDao().clearExpenses() }
                                DeleteTarget.ORDER_HISTORY -> { db.gigDao().clearOrders() }
                                null -> {}
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Data Wiped", Toast.LENGTH_SHORT).show()
                                // 🟢 Fix 2: Only reset the target; the input state dies with the dialog
                                deleteTarget = null
                            }
                        }
                    },
                    enabled = deleteConfirmationInput == "DELETE",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                // 🟢 Fix 3: Removed redundant reset here too
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        )
    }
}

// Helper Composable (Reused)
@Composable
fun SettingsActionItem(title: String, subtitle: String, icon: ImageVector, iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun PermissionRow(title: String, isGranted: Boolean, icon: ImageVector, onClick: () -> Unit) {
    val statusColor = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800)
    val statusIcon = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (isGranted) statusColor else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(statusIcon, null, tint = statusColor)
    }
}

fun checkAccessibilityService(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return enabledServices.contains(context.packageName)
}