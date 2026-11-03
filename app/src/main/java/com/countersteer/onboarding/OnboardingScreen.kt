package com.countersteer.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val stepIndex = uiState.currentStep.ordinal
    val totalSteps = OnboardingStep.entries.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (uiState.currentStep != OnboardingStep.WELCOME) {
                        IconButton(onClick = { viewModel.previousStep() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                    }
                },
                label = "onboarding_step",
            ) { step ->
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep(
                        onNext = { viewModel.nextStep() },
                    )

                    OnboardingStep.API_KEY -> ApiKeyStep(
                        apiKey = uiState.apiKey,
                        validationState = uiState.apiKeyValidation,
                        error = uiState.apiKeyError,
                        onApiKeyChange = { viewModel.updateApiKey(it) },
                        onValidate = { viewModel.validateApiKey() },
                        onSkip = { viewModel.skipApiKey() },
                        onNext = { viewModel.nextStep() },
                    )

                    OnboardingStep.PERMISSIONS -> PermissionsStep(
                        locationGranted = uiState.locationPermissionGranted,
                        onLocationResult = { viewModel.updateLocationPermission(it) },
                        onComplete = {
                            viewModel.completeOnboarding(onOnboardingComplete)
                        },
                    )
                }
            }
        }
    }
}
