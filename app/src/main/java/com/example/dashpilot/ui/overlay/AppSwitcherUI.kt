// app/src/main/java/com/example/gigpilot/ui/overlay/AppSwitcherUI.kt

package com.example.dashpilot.ui.overlay

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.* // Import Animation
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate // Import Rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashpilot.data.ShiftTracker // Import Tracker

@Composable
fun AppSwitcherUI(
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // Subscribe to State
    val isBusy by ShiftTracker.isBusy.collectAsState()
    val isTracking by ShiftTracker.isTracking.collectAsState()

    // --- ANIMATION LOGIC ---
    val infiniteTransition = rememberInfiniteTransition(label = "timer_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Vertical Pill
    Column(
        modifier = Modifier
            .width(60.dp)
            .wrapContentHeight()
            .background(Color.Black, shape = RoundedCornerShape(30.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Google Maps
        AppIcon(
            label = "Maps",
            icon = Icons.Default.Map,
            color = Color(0xFF4285F4)
        ) { launchApp(context, "com.google.android.apps.maps") }

        // 2. DoorDash (Red D)
        AppIconText("DD", Color(0xFFFF3008)) {
            launchApp(context, "com.doordash.driverapp")
        }

        // 3. Grubhub (Orange GH)
        AppIconText("GH", Color(0xFFFF8000)) {
            launchApp(context, "com.grubhub.driver")
        }

        // 4. Uber (Black/White)
        AppIconText("Uber", Color.White) {
            launchApp(context, "com.ubercab.driver")
        }

        // 5. STATUS TOGGLE (Replaces Home)
        // If not tracking, it acts as a "Start Shift" button (or Home)
        // If tracking, it is the Busy/Idle toggle

        val buttonColor = when {
            !isTracking -> Color.Gray // Off Shift
            isBusy -> Color(0xFFFFD600) // Busy (Yellow)
            else -> Color(0xFF00C853)   // Searching (Green)
        }

        IconButton(
            onClick = {
                if (isTracking) {
                    ShiftTracker.toggleStatus(context)
                } else {
                    // Optional: You could start the shift here, or just go Home
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.let { context.startActivity(it) }
                }
            },
            modifier = Modifier
                .size(36.dp)
                .background(buttonColor, RoundedCornerShape(18.dp))
        ) {
            Icon(
                imageVector = if (isBusy) Icons.Default.HourglassFull else Icons.Default.HourglassEmpty,
                contentDescription = "Status",
                tint = Color.Black,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (isBusy) angle else 0f) // Spin only if busy
            )
        }

        // 6. Drag Handle
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Move",
            tint = Color.Gray,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
        )

        // 7. Close Button
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
        }
    }
}

// ... Keep Helper Functions (AppIcon, AppIconText, launchApp) below ...
@Composable
fun AppIcon(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp).background(Color.White.copy(alpha=0.1f), RoundedCornerShape(18.dp))) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun AppIconText(text: String, color: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp).background(color, RoundedCornerShape(18.dp))) {
        Text(text = text, color = if(color == Color.White) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

fun launchApp(context: Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "App not installed: $packageName", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error launching: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}