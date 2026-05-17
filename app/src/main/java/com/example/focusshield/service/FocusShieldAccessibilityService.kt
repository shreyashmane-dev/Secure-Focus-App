package com.example.focusshield.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.focusshield.data.ProtectionRepository

class FocusShieldAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            val previousViolations = ProtectionRepository.state.value.violationCount
            ProtectionRepository.markForegroundPackage(event.packageName?.toString())
            val currentState = ProtectionRepository.state.value
            if (currentState.isActive && currentState.violationCount > previousViolations) {
                AppNotificationHelper.showViolationNotification(
                    context = this,
                    title = "Focus Shield warning",
                    message = currentState.pendingWarning ?: "Suspicious activity detected."
                )
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        ProtectionRepository.markForegroundPackage(packageName)
    }
}
