package com.countersteer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.data.backup.BackupPreview
import com.countersteer.data.backup.RestoreMode
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.theme.CountersteerDesign
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showReplaceConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = remember { dateFormat.format(Date()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.previewImport(uri)
        }
    }

    if (showReplaceConfirmDialog && pendingRestoreUri != null) {
        ReplaceConfirmDialog(
            onConfirm = {
                showReplaceConfirmDialog = false
                viewModel.restore(pendingRestoreUri!!, RestoreMode.REPLACE)
                pendingRestoreUri = null
            },
            onDismiss = {
                showReplaceConfirmDialog = false
                pendingRestoreUri = null
            },
        )
    }

    Scaffold(
        topBar = {
            CountersteerTopBar(title = "Backup & Restore", onNavigateBack = onNavigateBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Export Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Export Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "Export all your rides, routes, contacts, markers, and settings " +
                            "to a ZIP file. API keys are not included.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    when (state) {
                        is BackupRestoreState.Exporting -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Exporting...")
                            }
                        }
                        is BackupRestoreState.ExportComplete -> {
                            val exportState = state as BackupRestoreState.ExportComplete
                            Text(
                                text = exportState.message,
                                color = if (exportState.success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    viewModel.resetState()
                                    exportLauncher.launch("countersteer-backup-$today.zip")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                            ) {
                                Text("Export Again")
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    exportLauncher.launch("countersteer-backup-$today.zip")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                            ) {
                                Text("Export Backup")
                            }
                        }
                    }
                }
            }

            // Import Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Import Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "Restore data from a previously exported backup file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    when (state) {
                        is BackupRestoreState.Importing -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Reading backup...")
                            }
                        }
                        is BackupRestoreState.PreviewReady -> {
                            val previewState = state as BackupRestoreState.PreviewReady
                            PreviewContent(
                                preview = previewState.preview,
                                onMerge = {
                                    viewModel.restore(previewState.uri, RestoreMode.MERGE)
                                },
                                onReplace = {
                                    pendingRestoreUri = previewState.uri
                                    showReplaceConfirmDialog = true
                                },
                                onCancel = {
                                    viewModel.resetState()
                                },
                            )
                        }
                        is BackupRestoreState.Restoring -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Restoring data...")
                            }
                        }
                        is BackupRestoreState.RestoreComplete -> {
                            val restoreState = state as BackupRestoreState.RestoreComplete
                            Text(
                                text = restoreState.message,
                                color = if (restoreState.success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    viewModel.resetState()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                            ) {
                                Text("Done")
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream",
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                            ) {
                                Text("Select Backup File")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    preview: BackupPreview,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    val dateFormat = remember {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Backup Contents:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        if (preview.exportDate > 0) {
            Text(
                text = "Created: ${dateFormat.format(Date(preview.exportDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        PreviewRow("Rides", preview.ridesCount)
        PreviewRow("Track Points", preview.trackPointsCount)
        PreviewRow("Saved Routes", preview.savedRoutesCount)
        PreviewRow("Contacts", preview.contactsCount)
        PreviewRow("Markers", preview.markersCount)
        PreviewRow("Favourite Locations", preview.favoriteLocationsCount)
        if (preview.hasPreferences) {
            Text(
                text = "Settings included",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onMerge,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
        ) {
            Text("Merge with Existing Data")
        }

        OutlinedButton(
            onClick = onReplace,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Replace All Data")
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PreviewRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReplaceConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Replace All Data?") },
        text = {
            Text(
                "This will delete all existing rides, routes, contacts, markers, and " +
                    "settings, then restore from the backup. This action cannot be undone.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Replace")
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
}
