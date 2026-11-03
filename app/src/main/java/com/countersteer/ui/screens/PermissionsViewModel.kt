package com.countersteer.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PermissionItem(
    val name: String,
    val description: String,
    val permission: String,
    val isGranted: Boolean,
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _permissions = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissions: StateFlow<List<PermissionItem>> = _permissions.asStateFlow()

    init { refreshPermissions() }

    fun refreshPermissions() {
        _permissions.value = listOf(
            PermissionItem(
                name = "Location",
                description = "Required for navigation and ride tracking",
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                isGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED,
            ),
            PermissionItem(
                name = "Notifications",
                description = "Ride tracking and alert notifications",
                permission = Manifest.permission.POST_NOTIFICATIONS,
                isGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
            ),
        )
    }
}
