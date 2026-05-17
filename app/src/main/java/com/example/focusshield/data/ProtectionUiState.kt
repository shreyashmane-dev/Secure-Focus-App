package com.example.focusshield.data

data class ProtectionUiState(
    val isActive: Boolean = false,
    val startedAt: Long? = null,
    val elapsedSeconds: Long = 0,
    val violationCount: Int = 0,
    val protectedAppPackage: String = "",
    val protectedAppLabel: String = "",
    val availableApps: List<InstalledApp> = emptyList(),
    val currentForegroundPackage: String = "Unknown",
    val isMonitoringServiceRunning: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val overlayRiskApps: List<String> = emptyList(),
    val isProtectedAppInstalled: Boolean = false,
    val sessionStatusText: String = "Ready",
    val monitoringSummary: String = "Accessibility, overlay scan, split-screen watch",
    val lastSessionDuration: String? = null,
    val lastSessionEndedAt: Long? = null,
    val pendingWarning: String? = null,
    val logs: List<SessionLog> = emptyList()
) {
    val formattedElapsed: String
        get() {
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            val seconds = elapsedSeconds % 60
            return "%02d:%02d:%02d".format(hours, minutes, seconds)
        }
}
