// app/src/main/java/com/example/gigpilot/ui/screens/CherryPickerScreen.kt

package com.example.dashpilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashpilot.data.GigPrefs
import com.example.dashpilot.model.GigOrder
import com.example.dashpilot.ui.overlay.OverlayController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CherryPickerScreen(overlayController: OverlayController) {
    val context = LocalContext.current
    val prefs = remember { GigPrefs(context) }
    val scope = rememberCoroutineScope()
    var testJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var masterEnabled by remember { mutableStateOf(prefs.isScrapingEnabled) }

    // --- NEW BINARY LOGIC VARS ---
    var targetMile by remember { mutableStateOf(prefs.targetDollarsPerMile.toString()) }
    var targetHour by remember { mutableStateOf(prefs.targetDollarsPerHour.toString()) }

    // Visual Vars
    var isOverlayDark by remember { mutableStateOf(prefs.isOverlayDarkTheme) }
    var xPos by remember { mutableFloatStateOf(prefs.overlayX.toFloat()) }
    var yPos by remember { mutableFloatStateOf(prefs.overlayY.toFloat()) }

    val fontSteps = listOf("Small", "Normal", "Large", "Huge")
    val fontValues = listOf(0.8f, 1.0f, 1.25f, 1.5f)
    var fontIndex by remember { mutableIntStateOf(fontValues.indexOfFirst { it == prefs.fontScale }.takeIf { it != -1 } ?: 1) }

    var themeExpanded by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                overlayController.hide()
                testJob?.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            overlayController.hide()
            testJob?.cancel()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // --- HEADER ---
        item {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Cherry Picker", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = masterEnabled, onCheckedChange = { masterEnabled = it; prefs.isScrapingEnabled = it })
                }
                Text(
                    text = if (masterEnabled) "Strategy Active" else "Strategy Paused",
                    color = if (masterEnabled) Color(0xFF00C853) else Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // --- STRATEGY SECTION ---
        item {
            Text("THE STRATEGY", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Column {
                // Intro text to explain the Binary Logic
                Text(
                    "Set your minimum acceptable rates. Orders below these targets show Red. Orders matching or above show Green.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Target $/Mile
                    ThresholdInput(
                        label = "Target $/Mile",
                        value = targetMile,
                        modifier = Modifier.weight(1f)
                    ) {
                        targetMile = it
                        prefs.targetDollarsPerMile = it.toFloatOrNull() ?: 0f
                    }

                    // Target $/Hour
                    ThresholdInput(
                        label = "Target $/Hour",
                        value = targetHour,
                        modifier = Modifier.weight(1f)
                    ) {
                        targetHour = it
                        prefs.targetDollarsPerHour = it.toFloatOrNull() ?: 0f
                    }
                }
            }
        }

        item {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("OVERLAY APPEARANCE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- APPEARANCE CONTROLS (Unchanged) ---
        item {
            Box {
                OutlinedTextField(
                    value = if (isOverlayDark) "Dark HUD" else "Light HUD",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("HUD Theme") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth().clickable { themeExpanded = true }
                )
                Box(Modifier.matchParentSize().clickable { themeExpanded = true })

                DropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                    DropdownMenuItem(text = { Text("Dark HUD") }, onClick = {
                        isOverlayDark = true; prefs.isOverlayDarkTheme = true; themeExpanded = false
                        overlayController.show(generateTestOrder())
                    })
                    DropdownMenuItem(text = { Text("Light HUD") }, onClick = {
                        isOverlayDark = false; prefs.isOverlayDarkTheme = false; themeExpanded = false
                        overlayController.show(generateTestOrder())
                    })
                }
            }
        }

        item {
            Column {
                SectionHeader("FONT SIZE: ${fontSteps[fontIndex]}")
                Slider(
                    value = fontIndex.toFloat(),
                    onValueChange = {
                        fontIndex = it.toInt()
                        prefs.fontScale = fontValues[fontIndex]
                        overlayController.show(generateTestOrder())
                    },
                    valueRange = 0f..3f, steps = 2
                )
            }
        }

        item {
            Column {
                SectionHeader("HORIZONTAL (X: ${xPos.toInt()})")
                Slider(
                    value = xPos,
                    valueRange = 0f..1000f,
                    steps = 19,
                    onValueChange = { xPos = it; prefs.overlayX = it.toInt(); overlayController.updatePosition() }
                )
            }
        }

        item {
            Column {
                SectionHeader("VERTICAL (Y: ${yPos.toInt()})")
                Slider(
                    value = yPos,
                    valueRange = 0f..2200f,
                    steps = 19,
                    onValueChange = { yPos = it; prefs.overlayY = it.toInt(); overlayController.updatePosition() }
                )
            }
        }

        item {
            Button(
                onClick = {
                    testJob?.cancel()
                    overlayController.show(generateTestOrder())
                    testJob = scope.launch {
                        delay(5000)
                        overlayController.hide()
                        testJob = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Test Overlay (5s Preview)")
            }
        }

        // BOTTOM SPACER
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// --- PRIVATE HELPERS ---
private fun generateTestOrder(): GigOrder {
    // Generates a "middling" order so users can likely see one Green and one Red metric
    val randomPrice = Random.nextDouble(4.0, 15.0)
    val randomMiles = Random.nextDouble(1.0, 8.0)
    val estimatedMinutes = (randomMiles * 4).toInt() + 5
    return GigOrder(platform = "DoorDash", price = randomPrice, distanceMiles = randomMiles, durationMinutes = estimatedMinutes)
}

@Composable private fun SectionHeader(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 4.dp, top = 8.dp))
}

@Composable private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray))
}

@Composable private fun ThresholdInput(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}