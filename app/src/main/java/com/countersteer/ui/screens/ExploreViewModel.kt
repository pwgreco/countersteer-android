package com.countersteer.ui.screens

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.location.LocationProvider
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

data class ExploreUiState(
    val selectedBearing: Double = 0.0,  // N
    val distanceKm: Double = 100.0,
    val routeShape: RouteShape = RouteShape.ROUND_TRIP,
    val isLoading: Boolean = false,
    val routes: List<Route> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val routeGenerator: RouteGenerator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val currentLocation: StateFlow<Location?> = locationProvider.currentLocation

    init {
        locationProvider.startLocationUpdates()
    }

    fun setBearing(bearing: Double) {
        _uiState.value = _uiState.value.copy(selectedBearing = bearing)
    }

    fun setDistance(distanceKm: Double) {
        _uiState.value = _uiState.value.copy(distanceKm = distanceKm)
    }

    fun setRouteShape(shape: RouteShape) {
        _uiState.value = _uiState.value.copy(routeShape = shape)
    }

    fun findRoutes() {
        val location = currentLocation.value ?: run {
            _uiState.value = _uiState.value.copy(error = "Location not available")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val origin = LatLng(location.latitude, location.longitude)
                val state = _uiState.value
                val routes = when (state.routeShape) {
                    RouteShape.ROUND_TRIP -> routeGenerator.generateExploreRoutes(
                        origin = origin,
                        bearingDegrees = state.selectedBearing,
                        distanceKm = state.distanceKm,
                    )
                    RouteShape.OUT_AND_BACK -> routeGenerator.generateOutAndBackRoutes(
                        origin = origin,
                        bearingDegrees = state.selectedBearing,
                        distanceKm = state.distanceKm,
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    routes = routes,
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

    fun clearRoutes() {
        _uiState.value = _uiState.value.copy(routes = emptyList())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
