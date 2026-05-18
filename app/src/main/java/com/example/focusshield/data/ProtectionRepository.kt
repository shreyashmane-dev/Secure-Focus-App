package com.example.focusshield.data

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ProtectionRepository {
    private const val PREFS_NAME = "focus_shield_prefs"
    private const val KEY_PROTECTED_PACKAGE = "protected_package"
    private const val KEY_LAST_DURATION = "last_duration"
    private const val KEY_LAST_ENDED_AT = "last_ended_at"
    private const val MAX_LOGS = 40
    private const val VIOLATION_DEBOUNCE_MS = 3_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    private var preferences: SharedPreferences? = null
    private var lastViolationSignature: String? = null
    private var lastViolationAt: Long = 0L

    private val _state = MutableStateFlow(
        ProtectionUiState(
            logs = listOf(
                SessionLog(type = LogType.Info, message = "Focus Shield is ready.", source = "System")
            )
        )
    )
    val state: StateFlow<ProtectionUiState> = _state

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPackage = preferences?.getString(KEY_PROTECTED_PACKAGE, "").orEmpty()
        val availableApps = loadLaunchableApps(context)
        val savedApp = availableApps.firstOrNull { it.packageName == savedPackage }
        _state.update {
            it.copy(
                protectedAppPackage = savedPackage,
                protectedAppLabel = savedApp?.label.orEmpty(),
                availableApps = availableApps,
                lastSessionDuration = preferences?.getString(KEY_LAST_DURATION, null),
                lastSessionEndedAt = preferences?.getLong(KEY_LAST_ENDED_AT, 0L)?.takeIf { value -> value > 0L }
            )
        }
        refreshPermissionState(context)
    }

    fun setProtectedPackage(packageName: String) {
        val trimmed = packageName.trim()
        val selectedApp = state.value.availableApps.firstOrNull { it.packageName == trimmed }
        preferences?.edit()?.putString(KEY_PROTECTED_PACKAGE, trimmed)?.apply()
        _state.update {
            it.copy(
                protectedAppPackage = trimmed,
                protectedAppLabel = selectedApp?.label.orEmpty()
            )
        }
    }

    fun refreshPermissionState(context: Context) {
        initialize(context)
        val apps = if (state.value.availableApps.isEmpty()) loadLaunchableApps(context) else state.value.availableApps
        val overlays = findOverlayRiskApps(context)
        val protectedAppPackage = state.value.protectedAppPackage
        val selectedApp = apps.firstOrNull { it.packageName == protectedAppPackage }
        _state.update {
            it.copy(
                availableApps = apps,
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context),
                hasNotificationPermission = hasNotificationPermission(context),
                overlayRiskApps = overlays,
                isProtectedAppInstalled = isPackageInstalled(context, protectedAppPackage),
                protectedAppLabel = selectedApp?.label.orEmpty(),
                sessionStatusText = when {
                    it.isActive -> "Protection Mode active"
                    protectedAppPackage.isBlank() -> "Choose an exam app"
                    !isPackageInstalled(context, protectedAppPackage) -> "Protected app not found on this device"
                    !isAccessibilityServiceEnabled(context) -> "Accessibility is recommended for app-switch detection"
                    overlays.isNotEmpty() -> "Overlay-capable apps detected"
                    else -> "Ready to start"
                }
            )
        }
    }

    fun canStartProtection(context: Context): Boolean {
        refreshPermissionState(context)
        val current = state.value
        if (current.protectedAppPackage.isBlank()) {
            addWarning("Select the exam app before starting protection.", "Setup")
            return false
        }
        if (!current.isProtectedAppInstalled) {
            addWarning("The protected app package is not installed on this device.", "Setup")
            return false
        }
        if (current.overlayRiskApps.isNotEmpty()) {
            addInfo(
                "Overlay-capable apps are enabled: ${current.overlayRiskApps.joinToString(limit = 3)}",
                "Overlay"
            )
        }
        if (!current.isAccessibilityEnabled) {
            addInfo("Accessibility is off, so app switching cannot be monitored yet.", "Permissions")
        }
        return true
    }

    fun startProtection(context: Context) {
        initialize(context)
        val startedAt = System.currentTimeMillis()
        lastViolationSignature = null
        lastViolationAt = 0L
        _state.update {
            it.copy(
                isActive = true,
                startedAt = startedAt,
                elapsedSeconds = 0,
                violationCount = 0,
                isMonitoringServiceRunning = true,
                currentForegroundPackage = it.protectedAppPackage,
                sessionStatusText = "Protection Mode active",
                pendingWarning = null,
                logs = listOf(
                    SessionLog(
                        type = LogType.Info,
                        message = "Protection Mode started for ${it.protectedAppLabel.ifBlank { it.protectedAppPackage }}.",
                        source = "Session"
                    )
                )
            )
        }
        refreshPermissionState(context)
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1_000)
                _state.update { current ->
                    val start = current.startedAt ?: System.currentTimeMillis()
                    current.copy(elapsedSeconds = (System.currentTimeMillis() - start) / 1_000)
                }
            }
        }
    }

    fun stopProtection() {
        timerJob?.cancel()
        timerJob = null
        val duration = state.value.formattedElapsed
        val endedAt = System.currentTimeMillis()
        preferences?.edit()
            ?.putString(KEY_LAST_DURATION, duration)
            ?.putLong(KEY_LAST_ENDED_AT, endedAt)
            ?.apply()
        _state.update {
            it.copy(
                isActive = false,
                startedAt = null,
                isMonitoringServiceRunning = false,
                sessionStatusText = "Protection Mode stopped",
                lastSessionDuration = duration,
                lastSessionEndedAt = endedAt,
                pendingWarning = null,
                logs = appendLog(
                    it.logs,
                    SessionLog(
                        type = LogType.Info,
                        message = "Protection Mode stopped after $duration with ${it.violationCount} violation(s).",
                        source = "Session"
                    )
                )
            )
        }
    }

    fun clearWarning() {
        _state.update { it.copy(pendingWarning = null) }
    }

    fun markForegroundPackage(packageName: String?) {
        val cleanName = packageName.orEmpty().ifBlank { "Unknown" }
        val current = state.value
        _state.update { it.copy(currentForegroundPackage = cleanName) }

        if (!current.isActive) return
        val ownPackage = "com.example.focusshield"
        val protectedPackage = current.protectedAppPackage
        val allowed = cleanName == ownPackage || cleanName == protectedPackage
        if (!allowed) {
            addViolation("App switch detected: $cleanName", "Accessibility")
        } else if (cleanName == protectedPackage && current.currentForegroundPackage != protectedPackage) {
            addInfo("Returned to protected app: $cleanName", "Accessibility")
        }
    }

    fun recordSplitScreenAttempt() {
        if (state.value.isActive) {
            addViolation("Split screen or multi-window mode detected.", "Window")
        }
    }

    fun recordOverlayRisk(apps: List<String>) {
        if (apps.isNotEmpty()) {
            addViolation("Overlay risk detected: ${apps.joinToString(limit = 3)}", "Overlay")
        }
    }

    private fun addInfo(message: String, source: String) {
        _state.update {
            it.copy(
                logs = appendLog(
                    it.logs,
                    SessionLog(type = LogType.Info, message = message, source = source)
                )
            )
        }
    }

    private fun addWarning(message: String, source: String) {
        _state.update {
            it.copy(
                pendingWarning = message,
                logs = appendLog(
                    it.logs,
                    SessionLog(type = LogType.Warning, message = message, source = source)
                )
            )
        }
    }

    private fun addViolation(message: String, source: String) {
        val now = System.currentTimeMillis()
        val signature = "$source|$message"
        if (signature == lastViolationSignature && now - lastViolationAt < VIOLATION_DEBOUNCE_MS) {
            return
        }
        lastViolationSignature = signature
        lastViolationAt = now
        _state.update {
            it.copy(
                violationCount = it.violationCount + 1,
                pendingWarning = message,
                logs = appendLog(
                    it.logs,
                    SessionLog(type = LogType.Violation, message = message, source = source)
                )
            )
        }
    }

    private fun appendLog(logs: List<SessionLog>, log: SessionLog): List<SessionLog> {
        return (listOf(log) + logs).take(MAX_LOGS)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.contains("${context.packageName}/", ignoreCase = true)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadLaunchableApps(context: Context): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(context.packageManager)?.toString().orEmpty()
                if (packageName == context.packageName || label.isBlank()) {
                    null
                } else {
                    InstalledApp(label = label, packageName = packageName)
                }
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun findOverlayRiskApps(context: Context): List<String> {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val packageManager = context.packageManager
        val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        return packages.mapNotNull { info ->
            val requested = info.requestedPermissions?.contains(android.Manifest.permission.SYSTEM_ALERT_WINDOW) == true
            if (!requested || info.packageName == context.packageName) return@mapNotNull null
            val applicationInfo = info.applicationInfo ?: return@mapNotNull null
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    applicationInfo.uid,
                    info.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    applicationInfo.uid,
                    info.packageName
                )
            }
            if (mode == AppOpsManager.MODE_ALLOWED) {
                packageManager.getApplicationLabel(applicationInfo).toString()
            } else {
                null
            }
        }.distinct().sorted()
    }
}
