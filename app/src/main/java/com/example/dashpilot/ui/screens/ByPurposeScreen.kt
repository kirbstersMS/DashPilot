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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByPurposeScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { GigDatabase.getDatabase(context) }

    val allEarnings by db.gigDao().getAllEarnings().collectAsState(initial = emptyList())
    val allShifts by db.gigDao().getAllShifts().collectAsState(initial = emptyList()) // [NEW] Fetch Shifts

    // VIBE: Combine Platforms (Earnings) + Purposes (Shifts)
    val platforms = remember(allEarnings, allShifts) {
        val earningNames = allEarnings.map { it.platform }
        val shiftNames = allShifts.map { it.purpose }

        val allNames = (earningNames + shiftNames).distinct()

        // Normalize (DoorDash vs doordash) & Sort
        val consolidated = allNames.groupBy { it.lowercase() }
            .map { (_, variants) -> variants.minOf { it } } // Prefer Uppercase
            .sorted()

        // Move "Tips" to bottom
        val (tips, others) = consolidated.partition { it.equals("Tips", ignoreCase = true) }
        others + tips
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Platforms") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { p ->
        LazyColumn(Modifier.padding(p).padding(16.dp)) {
            items(platforms) { platform ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { navController.navigate(Screen.ServiceDetail.createRoute(platform)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(platform, style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            if (platforms.isEmpty()) {
                item { Text("No data yet.", Modifier.padding(16.dp)) }
            }
        }
    }
}