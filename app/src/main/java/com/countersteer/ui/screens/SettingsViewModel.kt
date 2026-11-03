package com.countersteer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val speedAlertEnabled: StateFlow<Boolean> = preferencesRepository.speedAlertEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    suspend fun setSpeedAlertEnabled(enabled: Boolean) {
        preferencesRepository.setSpeedAlertEnabled(enabled)
    }
}
