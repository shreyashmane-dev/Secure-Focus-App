package com.example.focusshield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusshield.data.ProtectionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: ProtectionUiState,
    onProtectedPackageChange: (String) -> Unit,
    onStartProtection: () -> Unit,
    onStopProtection: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAppName = when {
        state.protectedAppLabel.isNotBlank() -> state.protectedAppLabel
        state.protectedAppPackage.isNotBlank() -> state.protectedAppPackage
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeading(
            title = "Focus Shield",
            subtitle = "Temporary protection for online exam focus sessions."
        )

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Protection Mode", style = MaterialTheme.typography.titleMedium)
                    Text(state.formattedElapsed, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(if (state.isActive) "Active" else "Inactive", state.isActive)
            }

            Text(
                text = state.sessionStatusText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = expanded && !state.isActive,
                onExpandedChange = { expanded = !expanded && !state.isActive }
            ) {
                OutlinedTextField(
                    value = selectedAppName,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !state.isActive,
                    label = { Text("Select exam app") },
                    placeholder = { Text("Choose installed app") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded && !state.isActive,
                    onDismissRequest = { expanded = false }
                ) {
                    state.availableApps.forEach { app ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = {
                                onProtectedPackageChange(app.packageName)
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (state.protectedAppPackage.isNotBlank()) {
                Text(
                    text = state.protectedAppPackage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onStartProtection,
                enabled = !state.isActive && state.protectedAppPackage.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Protection")
            }

            OutlinedButton(
                onClick = onStopProtection,
                enabled = state.isActive,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop Protection")
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Session Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MetricRow("Selected app", state.protectedAppLabel.ifBlank { "Not selected" })
            MetricRow("Installed", if (state.isProtectedAppInstalled) "Yes" else "No")
            MetricRow("Violations", state.violationCount.toString())
            MetricRow("Monitoring", if (state.isMonitoringServiceRunning) "Running" else "Stopped")
            MetricRow("Last duration", state.lastSessionDuration ?: "None")
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text("Readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (state.isAccessibilityEnabled) "Accessibility ready" else "Accessibility off") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (state.hasNotificationPermission) "Notifications ready" else "Notifications needed") },
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) }
                )
            }
        }
    }
}
