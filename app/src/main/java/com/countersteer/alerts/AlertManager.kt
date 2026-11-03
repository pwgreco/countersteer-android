package com.countersteer.alerts

import android.content.Context
import android.media.RingtoneManager
import android.util.Log
import com.countersteer.data.dao.MarkerDao
import com.countersteer.data.entity.RoadMarkerEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the rider's position and triggers alerts when approaching
 * road markers (speed cameras, hazards, etc.).
 */
@Singleton
class AlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val markerDao: MarkerDao,
) {
    companion object {
        private const val TAG = "AlertManager"
        private const val NEARBY_QUERY_RADIUS_M = 5000.0 // query markers within 5 km
        private const val DISMISS_DISTANCE_FACTOR = 1.5 // dismiss when distance > 1.5x alert radius
    }

    private val _activeAlert = MutableStateFlow<AlertInfo?>(null)
    val activeAlert: StateFlow<AlertInfo?> = _activeAlert.asStateFlow()

    // Track which marker IDs have already been alerted in this ride
    private val alertedMarkerIds = mutableSetOf<Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Mutex to prevent concurrent updatePosition checks from piling up
    private val checkMutex = Mutex()

    /**
     * Call when a new ride/navigation starts to reset alert history.
     */
    fun startRide() {
        alertedMarkerIds.clear()
        _activeAlert.value = null
    }

    /**
     * Manually dismiss the current active alert (e.g. user tapped the banner).
     */
    fun dismissAlert() {
        _activeAlert.value = null
    }

    /**
     * Call when navigation ends.
     */
    fun stopRide() {
        alertedMarkerIds.clear()
        _activeAlert.value = null
    }

    /**
     * Update the rider's current position. Checks for nearby markers
     * and triggers or dismisses alerts accordingly.
     * Uses a Mutex to prevent coroutine pileup when checks take longer than the update interval.
     */
    fun updatePosition(latitude: Double, longitude: Double) {
        scope.launch {
            // If a previous check is still running, skip this update
            if (!checkMutex.tryLock()) return@launch
            try {
                checkAlerts(latitude, longitude)
            } catch (e: Exception) {
                Log.w(TAG, "Alert check failed", e)
            } finally {
                checkMutex.unlock()
            }
        }
    }

    private suspend fun checkAlerts(lat: Double, lon: Double) {
        // Check if current alert should be dismissed (passed the marker)
        val current = _activeAlert.value
        if (current != null) {
            val distToCurrent = haversineMeters(lat, lon, current.latitude, current.longitude)
            // Find the marker to get its alert radius
            val markers = markerDao.getNearby(
                current.latitude,
                current.longitude,
                100.0,
            ).first()
            val marker = markers.firstOrNull { it.id == current.markerId }
            val alertRadius = marker?.alertRadiusM ?: 1000
            if (distToCurrent > alertRadius * DISMISS_DISTANCE_FACTOR) {
                _activeAlert.value = null
            }
        }

        // Query nearby markers
        val nearbyMarkers = markerDao.getNearby(lat, lon, NEARBY_QUERY_RADIUS_M).first()
        if (nearbyMarkers.isEmpty()) return

        // Find the closest marker that hasn't been alerted yet and is within its alert radius
        var closestMarker: RoadMarkerEntity? = null
        var closestDist = Double.MAX_VALUE

        for (marker in nearbyMarkers) {
            if (marker.id in alertedMarkerIds) continue

            val dist = haversineMeters(lat, lon, marker.latitude, marker.longitude)
            if (dist <= marker.alertRadiusM && dist < closestDist) {
                closestDist = dist
                closestMarker = marker
            }
        }

        if (closestMarker != null) {
            val alert = AlertInfo(
                markerId = closestMarker.id,
                type = closestMarker.type,
                distanceMeters = closestDist.toInt(),
                note = closestMarker.note,
                latitude = closestMarker.latitude,
                longitude = closestMarker.longitude,
            )
            _activeAlert.value = alert
            alertedMarkerIds.add(closestMarker.id)
            playAlertChime()
        }
    }

    private fun playAlertChime() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play alert chime", e)
        }
    }

    private fun haversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val r = 6371000.0 // Earth radius in metres
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
