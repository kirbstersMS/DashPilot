package com.example.dashpilot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.example.dashpilot.R

class UberOverlayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Loop through all instances of this widget (in case you placed two)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    // 1. Create the Intent that opens the specific setting
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        // This URI targets Uber Driver specifically
        data = "package:com.ubercab.driver".toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    // 2. Wrap it in a PendingIntent
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // 3. Attach it to the layout
    val views = RemoteViews(context.packageName, R.layout.widget_uber_shortcut)
    views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

    // 4. Update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}