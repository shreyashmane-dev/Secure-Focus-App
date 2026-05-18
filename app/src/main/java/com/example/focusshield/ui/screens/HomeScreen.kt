package com.example.focusshield.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.focusshield.data.InstalledApp
import com.example.focusshield.data.ProtectionUiState

@Composable
fun HomeScreen(
    state: ProtectionUiState,
    onProtectedPackageChange: (String) -> Unit,
    onStartProtection: () -> Unit,
    onStopProtection: () -> Unit
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val selectedAppName = when {
        state.protectedAppLabel.isNotBlank() -> state.protectedAppLabel
        state.protectedAppPackage.isNotBlank() -> state.protectedAppPackage
        else -> "Choose installed exam app"
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isActive) { pickerOpen = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null)
                        Column {
                            Text(
                                text = selectedAppName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = state.protectedAppPackage.ifBlank { "Tap to choose the protected app" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "Select",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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

    if (pickerOpen) {
        AppPickerDialog(
            apps = state.availableApps,
            selectedPackage = state.protectedAppPackage,
            onDismiss = { pickerOpen = false },
            onSelect = { app ->
                onProtectedPackageChange(app.packageName)
                pickerOpen = false
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<InstalledApp>,
    selectedPackage: String,
    onDismiss: () -> Unit,
    onSelect: (InstalledApp) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.label.lowercase().contains(needle) || app.packageName.lowercase().contains(needle)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Exam App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    label = { Text("Search apps") },
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 420.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (filteredApps.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No apps match your search.", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Try another app name or package.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(app) },
                                    color = if (app.packageName == selectedPackage) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = app.label,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (app.packageName == selectedPackage) {
                                                Text(
                                                    text = "Selected",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
