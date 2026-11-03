package com.countersteer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.data.entity.OfflineMapRegionEntity
import com.countersteer.data.offline.DownloadProgress
import com.countersteer.ui.components.MapViewComposable
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.theme.CountersteerDesign
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onNavigateBack: () -> Unit,
    viewModel: OfflineMapsViewModel = hiltViewModel(),
) {
    val regions by viewModel.regions.collectAsState()
    val totalStorage by viewModel.totalStorageUsed.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val showNewRegionDialog by viewModel.showNewRegionDialog.collectAsState()

    Scaffold(
        topBar = {
            CountersteerTopBar(title = "Offline Maps", onNavigateBack = onNavigateBack)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showNewRegionDialog() },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Download new region")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Storage summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Total Offline Storage",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatSize(totalStorage),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${regions.size} region${if (regions.size != 1) "s" else ""} downloaded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Download progress
            if (downloadProgress != null) {
                item {
                    DownloadProgressCard(downloadProgress!!, onDismiss = { viewModel.clearProgress() })
                }
            }

            // Region list
            if (regions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No offline regions",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to download a map region for offline use",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(regions, key = { it.id }) { region ->
                RegionCard(
                    region = region,
                    isStale = viewModel.isRegionStale(region),
                    onDelete = { viewModel.deleteRegion(region) },
                )
            }

            // Bottom spacer for FAB
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showNewRegionDialog) {
        NewRegionDialog(
            onDismiss = { viewModel.dismissNewRegionDialog() },
            onDownload = { name, bounds, minZoom, maxZoom ->
                viewModel.downloadRegion(name, bounds, minZoom, maxZoom)
            },
            getEstimatedSize = { bounds, minZoom, maxZoom ->
                viewModel.getEstimatedSize(bounds, minZoom, maxZoom)
            },
        )
    }
}

@Composable
private fun DownloadProgressCard(progress: DownloadProgress, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (progress.error != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (progress.isComplete) {
                    "Download complete"
                } else if (progress.error != null) {
                    "Download failed"
                } else {
                    "Downloading: ${progress.regionName}"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (progress.error != null) {
                Text(
                    text = progress.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!progress.isComplete) {
                LinearProgressIndicator(
                    progress = { progress.percentage / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${progress.percentage.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (progress.isComplete || progress.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun RegionCard(
    region: OfflineMapRegionEntity,
    isStale: Boolean,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatSize(region.sizeBytes)} | Downloaded ${dateFormat.format(Date(region.downloadedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Last updated: ${dateFormat.format(Date(region.lastUpdatedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isStale) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Update available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Up to date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete region",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Region") },
            text = { Text("Delete \"${region.name}\" and its offline tiles? This will free ${formatSize(region.sizeBytes)}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun NewRegionDialog(
    onDismiss: () -> Unit,
    onDownload: (name: String, bounds: LatLngBounds, minZoom: Int, maxZoom: Int) -> Unit,
    getEstimatedSize: (bounds: LatLngBounds, minZoom: Int, maxZoom: Int) -> Long,
) {
    var regionName by remember { mutableStateOf("") }
    var currentBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    var estimatedSize by remember { mutableStateOf(0L) }
    var showSizeWarning by remember { mutableStateOf(false) }
    val minZoom = 6
    val maxZoom = 16

    // Update estimated size when bounds change
    LaunchedEffect(currentBounds) {
        currentBounds?.let { bounds ->
            estimatedSize = getEstimatedSize(bounds, minZoom, maxZoom)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download New Region") },
        text = {
            Column {
                OutlinedTextField(
                    value = regionName,
                    onValueChange = { regionName = it },
                    label = { Text("Region name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.input),
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pan and zoom the map to select the region to download:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                ) {
                    MapViewComposable(
                        modifier = Modifier.fillMaxSize(),
                        initialCenter = LatLng(40.0, -100.0), // Center of US
                        initialZoom = 5.0,
                        showUserLocation = false,
                        onMapReady = { map ->
                            map.addOnCameraIdleListener {
                                val projection = map.projection
                                currentBounds = projection.visibleRegion.latLngBounds
                            }
                            // Get initial bounds
                            currentBounds = map.projection.visibleRegion.latLngBounds
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estimated download size: ${formatSize(estimatedSize)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (estimatedSize > 500 * 1024 * 1024) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (estimatedSize > 500 * 1024 * 1024) {
                    Text(
                        text = "Warning: Large download. Zoom in for a smaller region.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bounds = currentBounds ?: return@Button
                    if (estimatedSize > 500 * 1024 * 1024) {
                        showSizeWarning = true
                    } else {
                        onDownload(
                            regionName.ifBlank { "Region ${System.currentTimeMillis()}" },
                            bounds,
                            minZoom,
                            maxZoom,
                        )
                    }
                },
                enabled = currentBounds != null,
                modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
            ) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
            ) {
                Text("Cancel")
            }
        },
    )

    if (showSizeWarning) {
        AlertDialog(
            onDismissRequest = { showSizeWarning = false },
            title = { Text("Large Download") },
            text = {
                Text(
                    "This region is estimated at ${formatSize(estimatedSize)}. " +
                        "Large downloads may take a long time and use significant storage. Continue?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSizeWarning = false
                        currentBounds?.let { bounds ->
                            onDownload(
                                regionName.ifBlank { "Region ${System.currentTimeMillis()}" },
                                bounds,
                                minZoom,
                                maxZoom,
                            )
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) {
                    Text("Download Anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSizeWarning = false },
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
