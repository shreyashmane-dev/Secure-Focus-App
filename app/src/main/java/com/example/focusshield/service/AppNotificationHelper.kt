package com.example.focusshield.service

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
import com.example.focusshield.MainActivity
import com.example.focusshield.R

object AppNotificationHelper {
    private const val SETUP_CHANNEL_ID = "focus_shield_setup"
    private const val SETUP_NOTIFICATION_ID = 2001
    private const val ALERT_CHANNEL_ID = "focus_shield_alerts"
    private const val ALERT_NOTIFICATION_ID = 2002

    fun showPermissionReadyNotification(context: Context) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SETUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Focus Shield notifications enabled")
            .setContentText("Setup alerts and protection session notifications are ready.")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(SETUP_NOTIFICATION_ID, notification)
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                SETUP_CHANNEL_ID,
                context.getString(R.string.notification_channel_setup),
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            NotificationChannel(
                ProtectionMonitoringService.CHANNEL_ID,
                context.getString(R.string.notification_channel_protection),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that a temporary focus protection session is active."
            },
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Focus Shield Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows focus violations while a protection session is active."
            }
        )
        manager.createNotificationChannels(channels)
    }

    fun showViolationNotification(context: Context, title: String, message: String) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
    }
}
