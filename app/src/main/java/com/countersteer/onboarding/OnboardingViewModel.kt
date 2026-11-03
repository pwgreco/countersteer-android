package com.countersteer.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.PreferencesRepository
import com.countersteer.data.SecurePreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

enum class OnboardingStep { WELCOME, API_KEY, PERMISSIONS }

enum class ApiKeyValidationState { IDLE, VALIDATING, VALID, INVALID }

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val apiKey: String = "",
    val apiKeyValidation: ApiKeyValidationState = ApiKeyValidationState.IDLE,
    val apiKeyError: String? = null,
    val locationPermissionGranted: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val httpClient: HttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.update { state ->
            val next = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.API_KEY
                OnboardingStep.API_KEY -> OnboardingStep.PERMISSIONS
                OnboardingStep.PERMISSIONS -> OnboardingStep.PERMISSIONS // last step
            }
            state.copy(currentStep = next)
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prev = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.WELCOME
                OnboardingStep.API_KEY -> OnboardingStep.WELCOME
                OnboardingStep.PERMISSIONS -> OnboardingStep.API_KEY
            }
            state.copy(currentStep = prev)
        }
    }

    fun updateApiKey(key: String) {
        _uiState.update {
            it.copy(
                apiKey = key,
                apiKeyValidation = ApiKeyValidationState.IDLE,
                apiKeyError = null,
            )
        }
    }

    fun validateApiKey() {
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank()) {
            _uiState.update {
                it.copy(
                    apiKeyValidation = ApiKeyValidationState.INVALID,
                    apiKeyError = "API key cannot be empty",
                )
            }
            return
        }

        _uiState.update { it.copy(apiKeyValidation = ApiKeyValidationState.VALIDATING) }

        viewModelScope.launch {
            try {
                val response: HttpResponse = httpClient.get(
                    "https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json?point=45.4215,-75.6972&key=$key&unit=KMPH",
                )
                if (response.status.value in 200..299) {
                    securePreferencesRepository.setTomTomApiKey(key)
                    _uiState.update {
                        it.copy(
                            apiKeyValidation = ApiKeyValidationState.VALID,
                            apiKeyError = null,
                        )
                    }
                } else if (response.status.value == 401 || response.status.value == 403) {
                    _uiState.update {
                        it.copy(
                            apiKeyValidation = ApiKeyValidationState.INVALID,
                            apiKeyError = "Invalid API key. Check your key and try again.",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            apiKeyValidation = ApiKeyValidationState.INVALID,
                            apiKeyError = "Unexpected response (${response.status.value}). Try again.",
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        apiKeyValidation = ApiKeyValidationState.INVALID,
                        apiKeyError = "Network error. Check your connection and try again.",
                    )
                }
            }
        }
    }

    fun skipApiKey() {
        nextStep()
    }

    fun updateLocationPermission(granted: Boolean) {
        _uiState.update { it.copy(locationPermissionGranted = granted) }
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingComplete(true)
            onComplete()
        }
    }
}
