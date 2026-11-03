package com.countersteer.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.theme.CountersteerDesign

@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CountersteerTopBar(title = "Permissions", onNavigateBack = onNavigateBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = CountersteerDesign.Spacing.md),
        ) {
            items(permissions, key = { it.permission }) { item ->
                PermissionRow(
                    item = item,
                    onGranted = { viewModel.refreshPermissions() },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    onGranted: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onGranted() }

    val icon = when {
        item.permission.contains("LOCATION") -> Icons.Default.LocationOn
        item.permission.contains("NOTIFICATION") -> Icons.Default.Notifications
        else -> Icons.Default.CheckCircle
    }

    // Track whether we've tried the launcher and it was a no-op (permanently denied)
    var triedLauncher by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    // Only consider permanently denied AFTER we've tried the launcher once and it didn't show
    val permanentlyDenied = triedLauncher && !item.isGranted && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, item.permission)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
            .padding(vertical = CountersteerDesign.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(CountersteerDesign.IconSize.lg),
            tint = if (item.isGranted) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(CountersteerDesign.Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(CountersteerDesign.Spacing.sm))
        if (item.isGranted) {
            Text(
                text = "Granted",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        } else {
            Button(
                onClick = {
                    if (permanentlyDenied) {
                        onOpenSettings()
                    } else {
                        triedLauncher = true
                        launcher.launch(item.permission)
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.topBarIcon),
            ) {
                Text(if (permanentlyDenied) "Open Settings" else "Grant")
            }
        }
    }
}
