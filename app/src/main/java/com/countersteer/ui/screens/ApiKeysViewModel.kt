package com.countersteer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class KeyValidationState { IDLE, VALIDATING, VALID, INVALID }

data class ApiKeysUiState(
    val currentKeyMasked: String? = null,
    val hasKey: Boolean = false,
    val isEditing: Boolean = false,
    val inputKey: String = "",
    val validationState: KeyValidationState = KeyValidationState.IDLE,
    val validationError: String? = null,
    val showRemoveDialog: Boolean = false,
)

@HiltViewModel
class ApiKeysViewModel @Inject constructor(
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val httpClient: HttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiKeysUiState())
    val uiState: StateFlow<ApiKeysUiState> = _uiState.asStateFlow()

    init {
        loadCurrentKey()
    }

    private fun loadCurrentKey() {
        val key = securePreferencesRepository.getTomTomApiKey()
        _uiState.update {
            it.copy(
                hasKey = key != null,
                currentKeyMasked = key?.let { k -> maskKey(k) },
            )
        }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 6) return "***"
        return "${key.take(3)}...${key.takeLast(3)}"
    }

    fun startEditing() {
        _uiState.update {
            it.copy(
                isEditing = true,
                inputKey = "",
                validationState = KeyValidationState.IDLE,
                validationError = null,
            )
        }
    }

    fun cancelEditing() {
        _uiState.update {
            it.copy(
                isEditing = false,
                inputKey = "",
                validationState = KeyValidationState.IDLE,
                validationError = null,
            )
        }
    }

    fun updateInputKey(key: String) {
        _uiState.update {
            it.copy(
                inputKey = key,
                validationState = KeyValidationState.IDLE,
                validationError = null,
            )
        }
    }

    fun validateKey() {
        val key = _uiState.value.inputKey.trim()
        if (key.isBlank()) {
            _uiState.update {
                it.copy(
                    validationState = KeyValidationState.INVALID,
                    validationError = "API key cannot be empty",
                )
            }
            return
        }

        _uiState.update { it.copy(validationState = KeyValidationState.VALIDATING) }

        viewModelScope.launch {
            try {
                val response: HttpResponse = httpClient.get(
                    "https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json?point=45.4215,-75.6972&key=$key&unit=KMPH",
                )
                if (response.status.value in 200..299) {
                    _uiState.update {
                        it.copy(
                            validationState = KeyValidationState.VALID,
                            validationError = null,
                        )
                    }
                } else if (response.status.value == 401 || response.status.value == 403) {
                    _uiState.update {
                        it.copy(
                            validationState = KeyValidationState.INVALID,
                            validationError = "Invalid API key. Check your key and try again.",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            validationState = KeyValidationState.INVALID,
                            validationError = "Unexpected response (${response.status.value}). Try again.",
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        validationState = KeyValidationState.INVALID,
                        validationError = "Network error. Check your connection and try again.",
                    )
                }
            }
        }
    }

    fun saveKey() {
        val key = _uiState.value.inputKey.trim()
        securePreferencesRepository.setTomTomApiKey(key)
        _uiState.update {
            it.copy(
                hasKey = true,
                currentKeyMasked = maskKey(key),
                isEditing = false,
                inputKey = "",
                validationState = KeyValidationState.IDLE,
                validationError = null,
            )
        }
    }

    fun showRemoveDialog() {
        _uiState.update { it.copy(showRemoveDialog = true) }
    }

    fun dismissRemoveDialog() {
        _uiState.update { it.copy(showRemoveDialog = false) }
    }

    fun removeKey() {
        securePreferencesRepository.clearTomTomApiKey()
        _uiState.update {
            ApiKeysUiState()
        }
    }
}
