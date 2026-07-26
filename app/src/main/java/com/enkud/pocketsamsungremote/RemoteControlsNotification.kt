package com.enkud.pocketsamsungremote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object RemoteControlsNotification {
    private const val CHANNEL_ID = "d_remote_controls_quiet"
    private const val LEGACY_CHANNEL_ID = "d_remote_controls"
    private const val NOTIFICATION_ID = 3007

    fun show(context: Context) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("D Remote")
            .setContentText("Quick TV controls")
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stat_remote, "Power", actionIntent(context, ACTION_REMOTE_POWER, 1))
            .addAction(R.drawable.ic_stat_remote, "Vol -", actionIntent(context, ACTION_REMOTE_VOL_DOWN, 2))
            .addAction(R.drawable.ic_stat_remote, "OK", actionIntent(context, ACTION_REMOTE_OK, 3))
            .addAction(R.drawable.ic_stat_remote, "Vol +", actionIntent(context, ACTION_REMOTE_VOL_UP, 4))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "D Remote controls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Lock-screen remote buttons for the Samsung TV"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RemoteActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
