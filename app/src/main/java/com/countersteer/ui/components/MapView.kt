package com.countersteer.ui.components

import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

@Composable
fun MapViewComposable(
    modifier: Modifier = Modifier,
    initialCenter: LatLng = LatLng(0.0, 0.0),
    initialZoom: Double = 14.0,
    showUserLocation: Boolean = true,
    onMapReady: ((MapLibreMap) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre once
    remember {
        MapLibre.getInstance(context)
        true
    }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(DARK_STYLE_URL) { style ->
                    // Enable user location blue dot if requested
                    if (showUserLocation) {
                        val locationComponentOptions = LocationComponentOptions.builder(context)
                            .pulseEnabled(true)
                            .build()

                        val activationOptions = LocationComponentActivationOptions
                            .builder(context, style)
                            .locationComponentOptions(locationComponentOptions)
                            .build()

                        map.locationComponent.apply {
                            activateLocationComponent(activationOptions)
                            try {
                                isLocationComponentEnabled = true
                                cameraMode = CameraMode.NONE
                                renderMode = RenderMode.COMPASS
                            } catch (_: SecurityException) {
                                // Permission not granted — location dot won't show
                            }
                        }
                    }
                    onMapReady?.invoke(map)
                }
                map.cameraPosition = CameraPosition.Builder()
                    .target(initialCenter)
                    .zoom(initialZoom)
                    .build()

                // Move attribution/logo to top-left so they don't overlap action buttons
                map.uiSettings.apply {
                    isLogoEnabled = true
                    logoGravity = Gravity.TOP or Gravity.START
                    isAttributionEnabled = true
                    attributionGravity = Gravity.TOP or Gravity.END
                }
            }
        }
    }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}
