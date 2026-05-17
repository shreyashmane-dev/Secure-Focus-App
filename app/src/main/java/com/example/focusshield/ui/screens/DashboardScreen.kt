package com.example.focusshield.ui.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusshield.data.LogType
import com.example.focusshield.data.ProtectionUiState
import com.example.focusshield.data.SessionLog

@Composable
fun DashboardScreen(state: ProtectionUiState) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading("Dashboard", "Live session telemetry")
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                Text("Live Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MetricRow("Protection status", if (state.isActive) "Active" else "Inactive")
                MetricRow("Timer", state.formattedElapsed)
                MetricRow("Violation count", state.violationCount.toString())
                MetricRow("Protected app", state.protectedAppPackage.ifBlank { "Not set" })
                MetricRow("Foreground app", state.currentForegroundPackage)
                MetricRow("Active monitoring", if (state.isMonitoringServiceRunning) "Foreground service" else "Stopped")
                MetricRow("Session state", state.sessionStatusText)
            }
        }

        item {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Monitoring Signals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
                Text(state.monitoringSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetricRow("Overlay risk apps", state.overlayRiskApps.size.toString())
                MetricRow("Accessibility", if (state.isAccessibilityEnabled) "Enabled" else "Disabled")
            }
        }

        item {
            Text("Session Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        if (state.logs.isEmpty()) {
            item {
                Text("No logs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(state.logs, key = { it.id }) { log ->
                LogRow(log = log, formattedTime = timeFormatter.format(Date(log.timestamp)))
            }
        }
    }
}

@Composable
private fun LogRow(log: SessionLog, formattedTime: String) {
    val color = when (log.type) {
        LogType.Info -> MaterialTheme.colorScheme.primary
        LogType.Warning -> MaterialTheme.colorScheme.secondary
        LogType.Violation -> MaterialTheme.colorScheme.error
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${log.type.name} | ${log.source}", color = color, fontWeight = FontWeight.SemiBold)
            Text(formattedTime, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(log.message)
    }
}
