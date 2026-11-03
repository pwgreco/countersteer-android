package com.countersteer.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val isTracking = AtomicBoolean(false)
    private val isNavigationMode = AtomicBoolean(false)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                _currentLocation.value = location
            }
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Switch between normal (5s) and navigation (2s) location update intervals.
     * Automatically restarts location updates if currently tracking.
     */
    fun setNavigationMode(enabled: Boolean) {
        val changed = if (enabled) {
            isNavigationMode.compareAndSet(false, true)
        } else {
            isNavigationMode.compareAndSet(true, false)
        }
        if (changed && isTracking.get()) {
            // Restart with new interval
            fusedClient.removeLocationUpdates(locationCallback)
            isTracking.set(false)
            startLocationUpdates()
        }
    }

    @Suppress("MissingPermission")
    fun startLocationUpdates() {
        if (!isTracking.compareAndSet(false, true)) return
        if (!hasLocationPermission()) {
            isTracking.set(false)
            return
        }

        val intervalMs = if (isNavigationMode.get()) 1_000L else 5_000L
        val minIntervalMs = if (isNavigationMode.get()) 500L else 2_000L

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs,
        )
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setWaitForAccurateLocation(false)
            .build()

        fusedClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper(),
        )

        // Also try to get last known location for quick initial position
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && _currentLocation.value == null) {
                _currentLocation.value = location
            }
        }
    }

    fun stopLocationUpdates() {
        if (!isTracking.compareAndSet(true, false)) return
        fusedClient.removeLocationUpdates(locationCallback)
    }
}
