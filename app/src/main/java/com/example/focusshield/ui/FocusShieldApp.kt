package com.example.focusshield.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.focusshield.data.ProtectionUiState
import com.example.focusshield.ui.screens.DashboardScreen
import com.example.focusshield.ui.screens.HomeScreen
import com.example.focusshield.ui.screens.SettingsScreen
import com.example.focusshield.ui.screens.SetupCenterScreen

private enum class FocusTab {
    Home,
    Dashboard,
    Settings
}

@Composable
fun FocusShieldApp(
    state: ProtectionUiState,
    onProtectedPackageChange: (String) -> Unit,
    onStartProtection: () -> Boolean,
    onStopProtection: () -> Unit,
    onClearWarning: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onShowTestNotification: () -> Unit,
    onRefreshSetup: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(FocusTab.Home) }
    var setupDismissed by rememberSaveable { mutableStateOf(false) }
    val needsSetup = !state.isAccessibilityEnabled && !setupDismissed

    LaunchedEffect(state.isAccessibilityEnabled) {
        if (state.isAccessibilityEnabled) {
            setupDismissed = false
        }
    }

    if (needsSetup && !setupDismissed) {
        SetupCenterScreen(
            state = state,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onShowTestNotification = onShowTestNotification,
            onRefreshStatus = onRefreshSetup,
            onContinue = { setupDismissed = true }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == FocusTab.Home,
                        onClick = { selectedTab = FocusTab.Home },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == FocusTab.Dashboard,
                        onClick = { selectedTab = FocusTab.Dashboard },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == FocusTab.Settings,
                        onClick = { selectedTab = FocusTab.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = selectedTab,
                label = "tab-content",
                modifier = Modifier.padding(padding)
            ) { tab ->
                when (tab) {
                    FocusTab.Home -> HomeScreen(
                        state = state,
                        onProtectedPackageChange = onProtectedPackageChange,
                        onStartProtection = {
                            if (onStartProtection()) {
                                selectedTab = FocusTab.Dashboard
                            }
                        },
                        onStopProtection = onStopProtection
                    )
                    FocusTab.Dashboard -> DashboardScreen(state = state)
                    FocusTab.Settings -> SettingsScreen(
                        state = state,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onShowTestNotification = onShowTestNotification,
                        onRefreshStatus = onRefreshSetup
                    )
                }
            }
        }
    }

    state.pendingWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = onClearWarning,
            title = { Text("Focus warning") },
            text = { Text(warning) },
            confirmButton = {
                TextButton(onClick = onClearWarning) {
                    Text("OK")
                }
            }
        )
    }
}
