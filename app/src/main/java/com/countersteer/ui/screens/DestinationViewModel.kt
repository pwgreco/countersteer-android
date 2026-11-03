package com.countersteer.ui.screens

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.dao.FavoriteLocationDao
import com.countersteer.data.dao.RouteDao
import com.countersteer.data.entity.FavoriteLocationEntity
import com.countersteer.data.entity.SavedRouteEntity
import com.countersteer.data.location.LocationProvider
import com.countersteer.routing.api.GeocodingResult
import com.countersteer.routing.api.NominatimClient
import com.countersteer.routing.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DestinationUiState(
    val searchQuery: String = "",
    val searchResults: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val selectedDestination: LatLng? = null,
    val selectedName: String? = null,
)

@HiltViewModel
class DestinationViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val nominatimClient: NominatimClient,
    private val routeDao: RouteDao,
    private val favoriteLocationDao: FavoriteLocationDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DestinationUiState())
    val uiState: StateFlow<DestinationUiState> = _uiState.asStateFlow()

    val currentLocation: StateFlow<Location?> = locationProvider.currentLocation

    val recentRoutes: StateFlow<List<SavedRouteEntity>> = routeDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favouriteRoutes: StateFlow<List<SavedRouteEntity>> = routeDao.getFavouriteRides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteLocationEntity>> = favoriteLocationDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQueryFlow = MutableStateFlow("")

    init {
        locationProvider.startLocationUpdates()
        observeSearchQuery()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(800)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.length >= 3) {
                        performSearch(query)
                    } else {
                        _uiState.value = _uiState.value.copy(searchResults = emptyList())
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        _searchQueryFlow.value = query
    }

    fun selectDestination(latLng: LatLng, name: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDestination = latLng,
            selectedName = name,
        )
    }

    fun selectFromMap(lat: Double, lon: Double) {
        _uiState.value = _uiState.value.copy(
            selectedDestination = LatLng(lat, lon),
            selectedName = "%.4f, %.4f".format(lat, lon),
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedDestination = null,
            selectedName = null,
        )
    }

    fun saveFavorite(name: String, latLng: LatLng) {
        viewModelScope.launch {
            favoriteLocationDao.insert(
                FavoriteLocationEntity(
                    name = name,
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                ),
            )
        }
    }

    fun deleteFavorite(id: Long) {
        viewModelScope.launch {
            favoriteLocationDao.deleteById(id)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, searchError = null)
        val nearLoc = currentLocation.value?.let {
            LatLng(it.latitude, it.longitude)
        }
        val results = nominatimClient.search(query, nearLocation = nearLoc)
        _uiState.value = _uiState.value.copy(
            searchResults = results,
            isSearching = false,
            searchError = if (nominatimClient.lastSearchFailed) "Search unavailable — check your connection" else null,
        )
    }
}
