package com.countersteer.ui.screens

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.dao.MarkerDao
import com.countersteer.data.entity.MarkerSource
import com.countersteer.data.entity.MarkerType
import com.countersteer.data.entity.RoadMarkerEntity
import com.countersteer.data.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val markerDao: MarkerDao,
) : ViewModel() {

    val currentLocation: StateFlow<Location?> = locationProvider.currentLocation

    val markers: StateFlow<List<RoadMarkerEntity>> = markerDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startLocation() {
        locationProvider.startLocationUpdates()
    }

    fun stopLocation() {
        locationProvider.stopLocationUpdates()
    }

    fun addMarker(latitude: Double, longitude: Double, type: MarkerType, note: String?) {
        viewModelScope.launch {
            markerDao.insert(
                RoadMarkerEntity(
                    latitude = latitude,
                    longitude = longitude,
                    type = type,
                    note = note,
                    source = MarkerSource.USER,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
