package com.countersteer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.dao.RideDao
import com.countersteer.data.dao.RouteDao
import com.countersteer.data.dao.TrackPointDao
import com.countersteer.data.entity.RideEntity
import com.countersteer.data.entity.RideTrackPointEntity
import com.countersteer.data.entity.SavedRouteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideSummaryViewModel @Inject constructor(
    private val rideDao: RideDao,
    private val trackPointDao: TrackPointDao,
    private val routeDao: RouteDao,
) : ViewModel() {

    private val _ride = MutableStateFlow<RideEntity?>(null)
    val ride: StateFlow<RideEntity?> = _ride.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<RideTrackPointEntity>>(emptyList())
    val trackPoints: StateFlow<List<RideTrackPointEntity>> = _trackPoints.asStateFlow()

    fun loadRide(rideId: Long) {
        viewModelScope.launch {
            _ride.value = rideDao.getById(rideId)
            trackPointDao.getByRideId(rideId).collect { points ->
                _trackPoints.value = points
            }
        }
    }

    fun discardRide() {
        val rideId = _ride.value?.id ?: return
        viewModelScope.launch {
            rideDao.delete(rideId)
        }
    }

    suspend fun saveForReride(): Long {
        val ride = _ride.value ?: return 0L
        val points = _trackPoints.value
        if (points.isEmpty()) return 0L

        return routeDao.insert(
            SavedRouteEntity(
                name = "Re-ride",
                createdAt = System.currentTimeMillis(),
                originLat = points.first().latitude,
                originLon = points.first().longitude,
                destinationLat = points.last().latitude,
                destinationLon = points.last().longitude,
                routePolyline = ride.routePolyline,
                routeType = ride.routeType,
                distanceKm = ride.distanceKm,
                valhallaJson = "",
                isFavouriteRide = false,
            ),
        )
    }

    fun saveAsFavourite(name: String) {
        val ride = _ride.value ?: return
        val points = _trackPoints.value
        if (points.isEmpty()) return

        viewModelScope.launch {
            val firstPoint = points.first()
            val lastPoint = points.last()

            routeDao.insert(
                SavedRouteEntity(
                    name = name,
                    createdAt = System.currentTimeMillis(),
                    originLat = firstPoint.latitude,
                    originLon = firstPoint.longitude,
                    destinationLat = lastPoint.latitude,
                    destinationLon = lastPoint.longitude,
                    routePolyline = ride.routePolyline,
                    routeType = ride.routeType,
                    distanceKm = ride.distanceKm,
                    valhallaJson = "",
                    isFavouriteRide = true,
                ),
            )
        }
    }
}
