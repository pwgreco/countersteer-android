package com.countersteer.ui.screens

import android.location.Location
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.dao.RouteDao
import com.countersteer.data.entity.RouteType
import com.countersteer.data.entity.SavedRouteEntity
import com.countersteer.data.location.LocationProvider
import com.countersteer.navigation.ActiveRouteHolder
import com.countersteer.routing.RouteGenerator
import com.countersteer.routing.model.LatLng
import com.countersteer.routing.model.Route
import com.countersteer.routing.model.RouteShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouteOptionsUiState(
    val routes: List<Route> = emptyList(),
    val nearbyFavourites: List<Route> = emptyList(),
    val selectedRouteType: RouteType = RouteType.CURVY,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class RouteOptionsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val locationProvider: LocationProvider,
    private val routeGenerator: RouteGenerator,
    private val activeRouteHolder: ActiveRouteHolder,
    private val routeDao: RouteDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteOptionsUiState())
    val uiState: StateFlow<RouteOptionsUiState> = _uiState.asStateFlow()

    val currentLocation: StateFlow<Location?> = locationProvider.currentLocation

    init {
        locationProvider.startLocationUpdates()
        loadRoutes()
    }

    fun selectRoute(routeType: RouteType) {
        _uiState.value = _uiState.value.copy(selectedRouteType = routeType)
    }

    private fun loadRoutes() {
        val mode = savedStateHandle.get<String>("mode") ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val location = locationProvider.currentLocation.value
                    ?: run {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Location not available",
                        )
                        return@launch
                    }
                val origin = LatLng(location.latitude, location.longitude)

                val routes = when (mode) {
                    "explore" -> {
                        val bearing = savedStateHandle.get<Float>("bearing")?.toDouble() ?: 0.0
                        val distance = savedStateHandle.get<Float>("distance")?.toDouble() ?: 100.0
                        val shapeName = savedStateHandle.get<String>("shape") ?: "ROUND_TRIP"
                        val shape = try {
                            RouteShape.valueOf(shapeName)
                        } catch (_: IllegalArgumentException) {
                            RouteShape.ROUND_TRIP
                        }
                        when (shape) {
                            RouteShape.ROUND_TRIP -> routeGenerator.generateExploreRoutes(origin, bearing, distance)
                            RouteShape.OUT_AND_BACK -> routeGenerator.generateOutAndBackRoutes(origin, bearing, distance)
                        }
                    }
                    "destination" -> {
                        val destLat = savedStateHandle.get<Float>("destLat")?.toDouble() ?: return@launch
                        val destLon = savedStateHandle.get<Float>("destLon")?.toDouble() ?: return@launch
                        val destination = LatLng(destLat, destLon)
                        routeGenerator.generateDestinationRoutes(origin, destination)
                    }
                    "favourite" -> {
                        val savedRouteId = savedStateHandle.get<Long>("savedRouteId") ?: 0L
                        val savedRoute = routeDao.getById(savedRouteId)
                        if (savedRoute != null) {
                            listOf(savedRoute.toRoute())
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }

                // Check for nearby favourite routes (only for explore/destination modes)
                val nearbyFavourites = if (mode == "favourite") {
                    emptyList()
                } else {
                    try {
                        val delta = 0.045 // ~5km
                        routeDao.getNearbyFavourites(
                            minLat = origin.latitude - delta,
                            maxLat = origin.latitude + delta,
                            minLon = origin.longitude - delta,
                            maxLon = origin.longitude + delta,
                        ).map { it.toRoute() }
                    } catch (_: Exception) { emptyList() }
                }

                _uiState.value = _uiState.value.copy(
                    routes = routes,
                    nearbyFavourites = nearbyFavourites,
                    isLoading = false,
                    selectedRouteType = routes.firstOrNull()?.type ?: RouteType.CURVY,
                    error = if (routes.isEmpty()) "No routes found" else null,
                )
            } catch (e: com.countersteer.routing.api.RouteGenerationException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to generate routes. Check your connection and try again.",
                )
            }
        }
    }

    /**
     * Caches the currently selected route into ActiveRouteHolder
     * so the navigation screen can access it.
     */
    fun prepareNavigation(): Boolean {
        val selected = _uiState.value.routes.firstOrNull {
            it.type == _uiState.value.selectedRouteType
        } ?: return false
        activeRouteHolder.setActiveRoute(selected)
        return true
    }

    fun retry() {
        loadRoutes()
    }
}

private fun SavedRouteEntity.toRoute(): Route {
    val geoPoints = try {
        val lineString = org.maplibre.geojson.LineString.fromJson(routePolyline)
        lineString.coordinates().map { coord ->
            LatLng(coord.latitude(), coord.longitude())
        }
    } catch (_: Exception) {
        emptyList()
    }
    val estimatedDuration = ((distanceKm / 50.0) * 3600).toLong()

    return Route(
        type = routeType,
        polyline = routePolyline,
        points = geoPoints,
        distanceKm = distanceKm,
        durationSeconds = estimatedDuration,
        durationWithTrafficSeconds = null,
        curvatureScore = 0.0,
        scenicScore = 0.0,
        maneuvers = emptyList(),
        speedLimits = emptyList(),
        hasUnpavedSegments = false,
        isOutAndBack = false,
    )
}
