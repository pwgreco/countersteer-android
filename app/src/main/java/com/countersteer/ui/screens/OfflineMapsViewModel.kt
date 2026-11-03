package com.countersteer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.entity.OfflineMapRegionEntity
import com.countersteer.data.offline.DownloadProgress
import com.countersteer.data.offline.MapUpdateWorker
import com.countersteer.data.offline.OfflineMapManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import javax.inject.Inject

@HiltViewModel
class OfflineMapsViewModel @Inject constructor(
    private val offlineMapManager: OfflineMapManager,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    val regions: StateFlow<List<OfflineMapRegionEntity>> = offlineMapManager.regions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadProgress: StateFlow<DownloadProgress?> = offlineMapManager.downloadProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalStorageUsed: StateFlow<Long> = offlineMapManager.regions
        .map { regions -> regions.sumOf { it.sizeBytes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _showNewRegionDialog = MutableStateFlow(false)
    val showNewRegionDialog: StateFlow<Boolean> = _showNewRegionDialog.asStateFlow()

    fun showNewRegionDialog() {
        _showNewRegionDialog.value = true
    }

    fun dismissNewRegionDialog() {
        _showNewRegionDialog.value = false
    }

    fun getEstimatedSize(bounds: LatLngBounds, minZoom: Int = 6, maxZoom: Int = 16): Long {
        return offlineMapManager.getEstimatedSize(bounds, minZoom, maxZoom)
    }

    fun downloadRegion(name: String, bounds: LatLngBounds, minZoom: Int = 6, maxZoom: Int = 16) {
        viewModelScope.launch {
            offlineMapManager.downloadRegion(name, bounds, minZoom, maxZoom)
            // Schedule background updates on first download
            MapUpdateWorker.schedule(context)
            _showNewRegionDialog.value = false
        }
    }

    fun deleteRegion(entity: OfflineMapRegionEntity) {
        viewModelScope.launch {
            offlineMapManager.deleteRegion(entity)
        }
    }

    fun clearProgress() {
        offlineMapManager.clearProgress()
    }

    fun isRegionStale(entity: OfflineMapRegionEntity): Boolean {
        val staleThresholdMs = 30L * 24 * 60 * 60 * 1000 // 30 days
        return (System.currentTimeMillis() - entity.lastUpdatedAt) > staleThresholdMs
    }
}
