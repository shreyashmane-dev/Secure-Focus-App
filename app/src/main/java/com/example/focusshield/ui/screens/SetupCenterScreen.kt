package com.example.focusshield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import com.example.focusshield.data.ProtectionUiState

@Composable
fun SetupCenterScreen(
    state: ProtectionUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onShowTestNotification: () -> Unit,
    onRefreshStatus: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading(
            title = "Setup Focus Shield",
            subtitle = "Approve the permissions Android uses for alerts and temporary protection."
        )

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Setup Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(state.sessionStatusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(
                    text = if (state.isAccessibilityEnabled && state.hasNotificationPermission && state.overlayRiskApps.isEmpty()) {
                        "Ready"
                    } else {
                        "Review"
                    },
                    active = state.isAccessibilityEnabled && state.hasNotificationPermission && state.overlayRiskApps.isEmpty()
                )
            }
            MetricRow("Notifications", if (state.hasNotificationPermission) "Granted" else "Needed")
            MetricRow("Accessibility", if (state.isAccessibilityEnabled) "Enabled" else "Needed")
            MetricRow("Overlay risks", state.overlayRiskApps.size.toString())
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Allow Android notifications so Focus Shield can show protection status and setup alerts.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Text("Request Notification Permission", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Text("Open App Notification Settings", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onShowTestNotification, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text("Send Test Notification", modifier = Modifier.padding(start = 8.dp))
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Accessibility Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Android must enable the Focus Shield accessibility service so app switching can be detected during a live protection session.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Text("Open Accessibility Settings", modifier = Modifier.padding(start = 8.dp))
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Overlay Safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.overlayRiskApps.isEmpty()) {
                Text("No enabled overlay-capable apps were found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(state.overlayRiskApps.joinToString(), color = MaterialTheme.colorScheme.error)
                Text(
                    "Disable risky floating apps before starting Protection Mode.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Security, contentDescription = null)
                Text("Open Overlay Settings", modifier = Modifier.padding(start = 8.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onRefreshStatus,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("Refresh Status", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}
