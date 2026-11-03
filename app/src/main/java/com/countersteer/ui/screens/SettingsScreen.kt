package com.countersteer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.theme.CountersteerDesign
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToOfflineMaps: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToApiKeys: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val speedAlertEnabled by viewModel.speedAlertEnabled.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CountersteerTopBar(title = "Settings")
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Navigation items
            item {
                SettingsCategory(title = "Maps & Navigation")
            }
            item {
                SettingsNavigationItem(
                    icon = Icons.Default.Map,
                    title = "Offline Maps",
                    subtitle = "Download map regions for offline use",
                    onClick = onNavigateToOfflineMaps,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // Speed Alerts
            item {
                SettingsToggleItem(
                    icon = Icons.Default.Speed,
                    title = "Speed Alerts",
                    subtitle = "Alert when exceeding speed limits",
                    checked = speedAlertEnabled,
                    onCheckedChange = { scope.launch { viewModel.setSpeedAlertEnabled(it) } },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // API Keys
            item {
                SettingsCategory(title = "Advanced")
            }
            item {
                SettingsNavigationItem(
                    icon = Icons.Default.VpnKey,
                    title = "API Keys",
                    subtitle = "Manage API keys for services",
                    onClick = onNavigateToApiKeys,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // Backup & Restore
            item {
                SettingsNavigationItem(
                    icon = Icons.Default.Cloud,
                    title = "Backup & Restore",
                    subtitle = "Export and import your data",
                    onClick = onNavigateToBackupRestore,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // Permissions
            item {
                SettingsNavigationItem(
                    icon = Icons.Default.Security,
                    title = "Permissions",
                    subtitle = "Manage app permissions",
                    onClick = onNavigateToPermissions,
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
