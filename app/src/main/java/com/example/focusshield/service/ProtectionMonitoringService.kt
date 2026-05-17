package com.example.focusshield.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.focusshield.MainActivity
import com.example.focusshield.R
import com.example.focusshield.data.ProtectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProtectionMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        AppNotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_PROTECTION) {
            ProtectionRepository.stopProtection()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (ProtectionRepository.state.value.isActive) {
                ProtectionRepository.refreshPermissionState(applicationContext)
                val risks = ProtectionRepository.state.value.overlayRiskApps
                if (risks.isNotEmpty()) {
                    ProtectionRepository.recordOverlayRisk(risks)
                }
                NotificationManagerCompat.from(this@ProtectionMonitoringService)
                    .notify(NOTIFICATION_ID, buildNotification())
                delay(5_000)
            }
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        if (ProtectionRepository.state.value.isActive) {
            ProtectionRepository.stopProtection()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, ProtectionActionReceiver::class.java).apply {
            action = ACTION_STOP_PROTECTION
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val state = ProtectionRepository.state.value
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Focus Shield is active")
            .setContentText("${state.formattedElapsed} | ${state.violationCount} violation(s)")
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_STOP_PROTECTION = "com.example.focusshield.action.STOP_PROTECTION"
        const val CHANNEL_ID = "focus_shield_protection"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ProtectionMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProtectionMonitoringService::class.java))
        }
    }
}
