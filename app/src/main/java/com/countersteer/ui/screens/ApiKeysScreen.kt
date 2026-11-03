package com.countersteer.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.theme.CountersteerDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    onNavigateBack: () -> Unit,
    viewModel: ApiKeysViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showRemoveDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveDialog,
            title = { Text("Remove API Key") },
            text = { Text("Are you sure you want to remove your TomTom API key? Traffic data will be unavailable until a new key is added.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeKey()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissRemoveDialog,
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            CountersteerTopBar(title = "API Keys", onNavigateBack = onNavigateBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // TomTom API Key card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TomTom Traffic API",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Current key status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.hasKey) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Configured",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.currentKeyMasked ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Valid",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Not configured",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Not configured",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!uiState.isEditing) {
                        // Action buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::startEditing,
                                modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                            ) {
                                Text(if (uiState.hasKey) "Update Key" else "Add Key")
                            }
                            if (uiState.hasKey) {
                                OutlinedButton(
                                    onClick = viewModel::showRemoveDialog,
                                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                ) {
                                    Text("Remove Key")
                                }
                            }
                        }
                    } else {
                        // Editing mode
                        OutlinedTextField(
                            value = uiState.inputKey,
                            onValueChange = viewModel::updateInputKey,
                            label = { Text("TomTom API Key") },
                            placeholder = { Text("Paste your API key here") },
                            singleLine = true,
                            isError = uiState.validationState == KeyValidationState.INVALID,
                            supportingText = if (uiState.validationError != null) {
                                { Text(uiState.validationError!!) }
                            } else if (uiState.validationState == KeyValidationState.VALID) {
                                {
                                    Text(
                                        "Key validated successfully",
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.input),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (uiState.validationState) {
                                KeyValidationState.VALID -> {
                                    Button(
                                        onClick = viewModel::saveKey,
                                        modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                                    ) {
                                        Text("Save")
                                    }
                                }
                                KeyValidationState.VALIDATING -> {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Validating...")
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = viewModel::validateKey,
                                        enabled = uiState.inputKey.isNotBlank(),
                                        modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                                    ) {
                                        Text("Validate")
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = viewModel::cancelEditing,
                                modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Getting a TomTom API Key",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Get a free key at developer.tomtom.com \u2014 2,500 requests/day free. " +
                            "The key is used for real-time traffic data on your routes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
