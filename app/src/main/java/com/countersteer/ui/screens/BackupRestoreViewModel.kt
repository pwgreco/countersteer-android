package com.countersteer.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.backup.BackupManager
import com.countersteer.data.backup.BackupPreview
import com.countersteer.data.backup.RestoreManager
import com.countersteer.data.backup.RestoreMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BackupRestoreState {
    data object Idle : BackupRestoreState()
    data object Exporting : BackupRestoreState()
    data class ExportComplete(val success: Boolean, val message: String) : BackupRestoreState()
    data object Importing : BackupRestoreState()
    data class PreviewReady(val preview: BackupPreview, val uri: Uri) : BackupRestoreState()
    data object Restoring : BackupRestoreState()
    data class RestoreComplete(val success: Boolean, val message: String) : BackupRestoreState()
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val restoreManager: RestoreManager,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupRestoreState>(BackupRestoreState.Idle)
    val state: StateFlow<BackupRestoreState> = _state.asStateFlow()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupRestoreState.Exporting
            val result = backupManager.exportToUri(uri)
            _state.value = if (result.isSuccess) {
                BackupRestoreState.ExportComplete(true, "Backup exported successfully")
            } else {
                BackupRestoreState.ExportComplete(
                    false,
                    result.exceptionOrNull()?.message ?: "Export failed",
                )
            }
        }
    }

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupRestoreState.Importing
            val result = restoreManager.preview(uri)
            _state.value = if (result.isSuccess) {
                BackupRestoreState.PreviewReady(result.getOrThrow(), uri)
            } else {
                BackupRestoreState.RestoreComplete(
                    false,
                    result.exceptionOrNull()?.message ?: "Failed to read backup",
                )
            }
        }
    }

    fun restore(uri: Uri, mode: RestoreMode) {
        viewModelScope.launch {
            _state.value = BackupRestoreState.Restoring
            val result = restoreManager.restore(uri, mode)
            _state.value = if (result.isSuccess) {
                BackupRestoreState.RestoreComplete(true, "Data restored successfully")
            } else {
                BackupRestoreState.RestoreComplete(
                    false,
                    result.exceptionOrNull()?.message ?: "Restore failed",
                )
            }
        }
    }

    fun resetState() {
        _state.value = BackupRestoreState.Idle
    }
}
