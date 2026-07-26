package com.enkud.pocketsamsungremote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class RemoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RemoteWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_remote_controls).apply {
                setOnClickPendingIntent(R.id.widget_power, actionIntent(context, ACTION_REMOTE_POWER, 11))
                setOnClickPendingIntent(R.id.widget_vol_down, actionIntent(context, ACTION_REMOTE_VOL_DOWN, 12))
                setOnClickPendingIntent(R.id.widget_ok, actionIntent(context, ACTION_REMOTE_OK, 13))
                setOnClickPendingIntent(R.id.widget_vol_up, actionIntent(context, ACTION_REMOTE_VOL_UP, 14))
            }
        }

        private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, RemoteActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
