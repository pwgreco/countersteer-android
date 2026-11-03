package com.countersteer.logging

import android.location.Location
import android.util.Log
import com.countersteer.data.dao.RideDao
import com.countersteer.data.dao.TrackPointDao
import com.countersteer.data.entity.RideEntity
import com.countersteer.data.entity.RideTrackPointEntity
import com.countersteer.data.entity.RouteType
import com.countersteer.data.location.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

data class RideProgress(
    val rideId: Long = 0,
    val durationSeconds: Long = 0,
    val distanceKm: Double = 0.0,
    val isRecording: Boolean = false,
)

@Singleton
class RideLogger @Inject constructor(
    private val rideDao: RideDao,
    private val trackPointDao: TrackPointDao,
    private val locationProvider: LocationProvider,
    private val statsCalculator: RideStatsCalculator,
) {
    private companion object {
        const val MIN_DISTANCE_METERS = 5.0  // Ignore points closer than 5m (GPS noise)
    }

    private var scope: CoroutineScope? = null
    private var locationJob: Job? = null
    private var currentRideId: Long = 0
    private var startTimeMs: Long = 0
    private val trackPoints = mutableListOf<RideTrackPointEntity>()
    private val pendingPoints = mutableListOf<RideTrackPointEntity>()

    // Running accumulators to avoid recalculating from full list every update
    private var runningDistanceKm = 0.0
    private var runningMaxSpeedKmh = 0.0
    private var runningElevationGainM = 0.0
    private var lastPoint: RideTrackPointEntity? = null

    private val _progress = MutableStateFlow(RideProgress())
    val progress: StateFlow<RideProgress> = _progress.asStateFlow()

    fun startRide(routeType: RouteType) {
        if (_progress.value.isRecording) return

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        startTimeMs = System.currentTimeMillis()
        trackPoints.clear()
        pendingPoints.clear()
        runningDistanceKm = 0.0
        runningMaxSpeedKmh = 0.0
        runningElevationGainM = 0.0
        lastPoint = null

        scope?.launch {
            val ride = RideEntity(
                startedAt = startTimeMs,
                routeType = routeType,
            )
            currentRideId = rideDao.insert(ride)
            _progress.value = RideProgress(
                rideId = currentRideId,
                isRecording = true,
            )

            // Start collecting location updates
            locationJob = launch {
                locationProvider.currentLocation
                    .filterNotNull()
                    .collect { location ->
                        recordPoint(location)
                    }
            }
        }
    }

    private suspend fun recordPoint(location: Location) {
        if (currentRideId == 0L) return

        // Skip points too close to the last recorded point (GPS noise filter)
        lastPoint?.let { prev ->
            val distMeters = haversineKm(
                prev.latitude, prev.longitude,
                location.latitude, location.longitude,
            ) * 1000.0
            if (distMeters < MIN_DISTANCE_METERS) return
        }

        val point = RideTrackPointEntity(
            rideId = currentRideId,
            timestamp = System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeM = if (location.hasAltitude()) location.altitude else 0.0,
            speedKmh = if (location.hasSpeed()) {
                (location.speed * 3.6).coerceAtLeast(0.0) // m/s to km/h
            } else {
                0.0
            },
            bearing = if (location.hasBearing()) {
                location.bearing.toDouble()
            } else {
                0.0
            },
            accuracyM = if (location.hasAccuracy()) location.accuracy else 0f,
        )

        // Update running accumulators incrementally
        if (point.speedKmh > runningMaxSpeedKmh) {
            runningMaxSpeedKmh = point.speedKmh
        }

        lastPoint?.let { prev ->
            runningDistanceKm += haversineKm(
                prev.latitude, prev.longitude,
                point.latitude, point.longitude,
            )
            val altDiff = point.altitudeM - prev.altitudeM
            if (altDiff > 0) {
                runningElevationGainM += altDiff
            }
        }
        lastPoint = point

        // Keep track points for final curvature calculation
        trackPoints.add(point)
        pendingPoints.add(point)

        // Batch insert every 5 points
        if (pendingPoints.size >= 5) {
            try {
                trackPointDao.insertAll(pendingPoints.toList())
                pendingPoints.clear()
            } catch (e: Exception) {
                Log.e("RideLogger", "Failed to batch insert track points", e)
                // Keep points in pending list to retry next batch
            }
        }

        // Update progress using running accumulators (O(1) per update)
        val elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000
        _progress.value = RideProgress(
            rideId = currentRideId,
            durationSeconds = elapsedSeconds,
            distanceKm = runningDistanceKm,
            isRecording = true,
        )

        // Update ride entity periodically (every 10 points)
        if (trackPoints.size % 10 == 0) {
            val avgSpeedKmh = if (elapsedSeconds > 0) {
                runningDistanceKm / (elapsedSeconds / 3600.0)
            } else {
                0.0
            }
            val stats = RideStats(
                distanceKm = runningDistanceKm,
                durationSeconds = elapsedSeconds,
                avgSpeedKmh = avgSpeedKmh,
                maxSpeedKmh = runningMaxSpeedKmh,
                elevationGainM = runningElevationGainM,
                curvatureScore = 0.0, // Calculated only at ride end
            )
            updateRideEntity(stats)
        }
    }

    suspend fun stopRide(): Long {
        locationJob?.cancel()
        locationJob = null

        // Flush remaining points
        if (pendingPoints.isNotEmpty()) {
            try {
                trackPointDao.insertAll(pendingPoints.toList())
                pendingPoints.clear()
            } catch (e: Exception) {
                Log.e("RideLogger", "Failed to flush remaining track points", e)
            }
        }

        // Compute final stats with full curvature calculation
        try {
            val stats = statsCalculator.calculate(trackPoints)
            updateRideEntity(stats, ended = true)
        } catch (e: Exception) {
            Log.e("RideLogger", "Failed to update final ride stats", e)
        }

        val rideId = currentRideId
        currentRideId = 0
        startTimeMs = 0
        trackPoints.clear()
        runningDistanceKm = 0.0
        runningMaxSpeedKmh = 0.0
        runningElevationGainM = 0.0
        lastPoint = null

        _progress.value = RideProgress()
        scope?.cancel()
        scope = null

        return rideId
    }

    private suspend fun updateRideEntity(stats: RideStats, ended: Boolean = false) {
        val ride = rideDao.getById(currentRideId) ?: return
        rideDao.update(
            ride.copy(
                endedAt = if (ended) System.currentTimeMillis() else ride.endedAt,
                distanceKm = stats.distanceKm,
                durationSeconds = stats.durationSeconds,
                avgSpeedKmh = stats.avgSpeedKmh,
                maxSpeedKmh = stats.maxSpeedKmh,
                elevationGainM = stats.elevationGainM,
                curvatureScore = stats.curvatureScore,
            ),
        )
    }

    private fun haversineKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun isRecording(): Boolean = _progress.value.isRecording
}
