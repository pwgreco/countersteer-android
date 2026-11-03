package com.countersteer.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.countersteer.ui.theme.CountersteerDesign

@Composable
fun ApiKeyStep(
    apiKey: String,
    validationState: ApiKeyValidationState,
    error: String?,
    onApiKeyChange: (String) -> Unit,
    onValidate: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "TomTom API Key",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "An API key from TomTom enables traffic-aware routing. " +
                "The app works without it, but routes won't consider live traffic.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "How to get a key:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "1. Go to developer.tomtom.com and create a free account\n" +
                "2. Go to Dashboard → Keys\n" +
                "3. Copy your API key\n" +
                "4. Paste it below\n\n" +
                "The free tier includes 2,500 requests/day,\n" +
                "more than enough for personal use.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = validationState == ApiKeyValidationState.INVALID,
            supportingText = {
                when {
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    validationState == ApiKeyValidationState.VALID -> Text(
                        "Key validated successfully",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            trailingIcon = {
                when (validationState) {
                    ApiKeyValidationState.VALIDATING -> CircularProgressIndicator(
                        modifier = Modifier.width(24.dp),
                    )
                    ApiKeyValidationState.VALID -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Valid",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    ApiKeyValidationState.INVALID -> Icon(
                        Icons.Default.Error,
                        contentDescription = "Invalid",
                        tint = MaterialTheme.colorScheme.error,
                    )
                    else -> {}
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onValidate,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
            shape = RoundedCornerShape(12.dp),
            enabled = apiKey.isNotBlank() && validationState != ApiKeyValidationState.VALIDATING,
        ) {
            Text(
                text = "Validate key",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (validationState == ApiKeyValidationState.VALID) {
                OutlinedButton(
                    onClick = onNext,
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
