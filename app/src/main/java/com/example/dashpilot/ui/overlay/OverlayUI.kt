package com.example.dashpilot.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashpilot.data.GigPrefs
import com.example.dashpilot.model.GigOrder

@Composable
fun OverlayHUD(order: GigOrder) {
    val context = LocalContext.current
    val prefs = GigPrefs(context)

    val minRequired = if (order.orderCount > 1) prefs.minPayBundleThreshold else prefs.minPayThreshold
    val isPricePass = order.price >= minRequired

    // --- THEME LOGIC ---
    val isDark = prefs.isOverlayDarkTheme
    val scale = prefs.fontScale

    val containerColor = if (isDark) Color.Black.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.95f)
    val baseTextColor = if (isDark) Color.White else Color.Black
    val labelColor = if (isDark) Color.Gray else Color.DarkGray

    // Scaled Fonts
    val labelSize = (10 * scale).sp
    val valueSize = (18 * scale).sp
    val headerSize = (22 * scale).sp // Slightly larger for the main Pay

    fun getColor(value: Double, low: Float, high: Float): Color {
        return when {
            value < low -> Color(0xFFFF5252) // Red
            value >= high -> Color(0xFF00C853) // Green
            else -> Color(0xFFFFD600) // Yellow
        }
    }

    val mileColor = getColor(order.dollarsPerMile, prefs.mileLowThreshold, prefs.mileHighThreshold)
    val hourlyColor = getColor(order.dollarsPerHour, prefs.hourlyLowThreshold, prefs.hourlyHighThreshold)
    val payColor = if (isPricePass) Color(0xFF00C853) else Color(0xFFFF5252)

    Card(
        modifier = Modifier
            .padding(start = 12.dp, top = 12.dp)
            .width((220 * scale).dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // --- HEADER: TOTAL PAY ---
            // "move the pay metric above the current 2x2 grid... onto its own smaller line"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center, // Centered for emphasis
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAY ",
                    color = labelColor,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline()
                )
                if (order.orderCount > 1) {
                    Text(
                        text = "(${order.orderCount}x) ",
                        color = Color.Yellow, // Alert the user it's a stack
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline()
                    )
                }
                Text(
                    text = "$${"%.2f".format(order.price)}",
                    color = payColor,
                    fontSize = headerSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha=0.3f)))
            Spacer(modifier = Modifier.height(8.dp))

            // --- ROW 1: TIME (Minutes | $/HR) ---
            Row(modifier = Modifier.fillMaxWidth()) {
                // Left: Raw Minutes
                Box(modifier = Modifier.weight(1f)) {
                    // FIX: Check isEstimate flag
                    val timeText = if (order.isEstimate) "~${order.durationMinutes}m" else "${order.durationMinutes}m"
                    GridCell("TIME", timeText, baseTextColor, labelColor, labelSize, valueSize)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: $/Hour
                Box(modifier = Modifier.weight(1f)) {
                    GridCell("$/HR", "$${"%.2f".format(order.dollarsPerHour)}", hourlyColor, labelColor, labelSize, valueSize)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha=0.3f)))
            Spacer(modifier = Modifier.height(8.dp))

            // --- ROW 2: DISTANCE (Miles | $/Mi) ---
            Row(modifier = Modifier.fillMaxWidth()) {
                // Left: Raw Miles
                Box(modifier = Modifier.weight(1f)) {
                    GridCell("DIST", "%.1f mi".format(order.distanceMiles), baseTextColor, labelColor, labelSize, valueSize)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: $/Mile
                Box(modifier = Modifier.weight(1f)) {
                    GridCell("$/MI", "$${"%.2f".format(order.dollarsPerMile)}", mileColor, labelColor, labelSize, valueSize)
                }
            }
        }
    }
}

@Composable
fun GridCell(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, color = labelColor, fontSize = labelSize, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = valueColor, fontSize = valueSize, fontWeight = FontWeight.Bold)
    }
}