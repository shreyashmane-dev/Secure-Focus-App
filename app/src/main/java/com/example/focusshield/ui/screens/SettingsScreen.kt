package com.example.focusshield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusshield.data.AuthUiState
import com.example.focusshield.data.ProtectionUiState

@Composable
fun SettingsScreen(
    state: ProtectionUiState,
    authState: AuthUiState,
    onLogout: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onShowTestNotification: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading(
            title = "Settings",
            subtitle = "Prepare the permissions used during active protection sessions."
        )

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MetricRow("Signed in", authState.email.ifBlank { authState.name })
            MetricRow("Role", authState.role)
            OutlinedButton(onClick = onLogout, enabled = !state.isActive, modifier = Modifier.fillMaxWidth()) {
                Text("Logout")
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Accessibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MetricRow("Accessibility service", if (state.isAccessibilityEnabled) "Enabled" else "Required")
            Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Text("Open Accessibility Settings", modifier = Modifier.padding(start = 8.dp))
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MetricRow("Notification permission", if (state.hasNotificationPermission) "Granted" else "Required")
            OutlinedButton(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Text("Allow Notifications", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Text("Open Notification Settings", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onShowTestNotification, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Text("Send Test Notification", modifier = Modifier.padding(start = 8.dp))
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Overlay Scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MetricRow("Overlay risk apps", state.overlayRiskApps.size.toString())
            if (state.overlayRiskApps.isNotEmpty()) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    state.overlayRiskApps.joinToString(),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text("No enabled overlay risks found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Text("Open Overlay Settings", modifier = Modifier.padding(start = 8.dp))
            }
        }

        OutlinedButton(onClick = onRefreshStatus, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text("Refresh Permission Status", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
