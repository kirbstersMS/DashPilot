package com.example.dashpilot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.dashpilot.data.GigDatabase
import com.example.dashpilot.ui.Screen
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllYearsScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { GigDatabase.getDatabase(context) }

    val allEarnings by db.gigDao().getAllEarnings().collectAsState(initial = emptyList())
    val allShifts by db.gigDao().getAllShifts().collectAsState(initial = emptyList())

    val years = remember(allEarnings, allShifts) {
        val ySet = mutableSetOf<Int>()
        val cal = Calendar.getInstance()
        allEarnings.forEach { cal.timeInMillis = it.date; ySet.add(cal.get(Calendar.YEAR)) }
        allShifts.forEach { cal.timeInMillis = it.date; ySet.add(cal.get(Calendar.YEAR)) }
        ySet.add(Calendar.getInstance().get(Calendar.YEAR))
        ySet.sortedDescending()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Years") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { p ->
        LazyColumn(Modifier.padding(p).padding(16.dp)) {
            items(years) { year ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { navController.navigate(Screen.YearlyRecap.createRoute(year)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(year.toString(), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}