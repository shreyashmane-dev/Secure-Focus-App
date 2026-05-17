package com.example.focusshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.focusshield.data.ProtectionRepository

class ProtectionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ProtectionMonitoringService.ACTION_STOP_PROTECTION) {
            ProtectionRepository.stopProtection()
            ProtectionMonitoringService.stop(context.applicationContext)
        }
    }
}
