package com.example.dashpilot.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.dashpilot.data.GigDatabase
import com.example.dashpilot.data.UnifiedCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyRecapScreen(navController: NavController, initialYear: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GigDatabase.getDatabase(context) }
    var currentYear by remember { mutableIntStateOf(initialYear) }

    // Fetch Data
    val allEarnings by db.gigDao().getAllEarnings().collectAsState(initial = emptyList())
    val allExpenses by db.gigDao().getAllExpenses().collectAsState(initial = emptyList())
    val allShifts by db.gigDao().getAllShifts().collectAsState(initial = emptyList())

    // Group By Month Logic
    val monthlyStats = remember(currentYear, allEarnings, allExpenses, allShifts) {
        val months = mutableListOf<MonthSummary>()
        val cal = Calendar.getInstance()

        for (m in 0..11) {
            cal.set(currentYear, m, 1, 0, 0, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis

            val mEarns = allEarnings.filter { it.date in start until end }
            val mExps = allExpenses.filter { it.date in start until end }
            val mShifts = allShifts.filter { it.date in start until end }

            months.add(MonthSummary(
                name = SimpleDateFormat("MMMM", Locale.US).format(Date(start)),
                earnings = mEarns.sumOf { it.amount },
                expenses = mExps.sumOf { it.amount },
                miles = mShifts.sumOf { it.miles },
                // VIBE FIX: Dynamic Deduction (0.70/mile)
                deduction = mShifts.sumOf { it.miles * 0.70 }
            ))
        }
        months
    }

    val totalEarn = monthlyStats.sumOf { it.earnings }
    val totalDed = monthlyStats.sumOf { it.deduction }
    val taxable = (totalEarn - totalDed - monthlyStats.sumOf { it.expenses }).coerceAtLeast(0.0)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { UnifiedCsv.exportToStream(db, it) }
                withContext(Dispatchers.Main) { Toast.makeText(context, "Full Ledger Exported", Toast.LENGTH_LONG).show() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$currentYear Recap") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { currentYear-- }) { Icon(Icons.Default.ChevronLeft, "Prev") }
                    IconButton(onClick = { currentYear++ }) { Icon(Icons.Default.ChevronRight, "Next") }
                    IconButton(onClick = { exportLauncher.launch("GigPilot_${currentYear}_Report.csv") }) { Icon(Icons.Default.Download, "Export") }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            // Tax Estimate Card
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Est. Taxable Income", style = MaterialTheme.typography.titleMedium)
                    Text("$${"%.2f".format(taxable)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("(Earnings - Expenses - Mileage Deduction)", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Table Header
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Month", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Earn", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Exp", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Ded.", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()

            // Month Rows
            LazyColumn {
                items(monthlyStats) { m ->
                    if (m.earnings > 0 || m.expenses > 0 || m.miles > 0) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(m.name.take(3), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text("${m.earnings.toInt()}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            Text("${m.expenses.toInt()}", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            Text("${m.deduction.toInt()}", Modifier.weight(1f), color = Color.Gray)
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

data class MonthSummary(val name: String, val earnings: Double, val expenses: Double, val miles: Double, val deduction: Double)