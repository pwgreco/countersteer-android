package com.countersteer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.countersteer.ui.components.RidingButton
import com.countersteer.ui.theme.CountersteerDesign
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.data.entity.RoadMarkerEntity
import com.countersteer.ui.components.MapViewComposable
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable
fun HomeScreen(
    onNavigateToExplore: () -> Unit = {},
    onNavigateToDestination: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val location by viewModel.currentLocation.collectAsState()
    val markers by viewModel.markers.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }

    // State for add-marker dialog
    var showAddMarker by remember { mutableStateOf(false) }
    var longPressLat by remember { mutableStateOf(0.0) }
    var longPressLon by remember { mutableStateOf(0.0) }

    // Start location updates when screen is active
    DisposableEffect(Unit) {
        viewModel.startLocation()
        onDispose {
            viewModel.stopLocation()
        }
    }

    // Auto-center on user location the first time we get it
    LaunchedEffect(location, mapInstance) {
        val loc = location
        val map = mapInstance
        if (loc != null && map != null && !hasCenteredOnUser) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(loc.latitude, loc.longitude),
                    14.0,
                ),
                1_000,
            )
            hasCenteredOnUser = true
        }
    }

    // Update markers on map when markers change
    LaunchedEffect(markers, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        drawMarkersOnMap(map, markers)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction("Add road marker at current location") {
                    val loc = location
                    if (loc != null) {
                        longPressLat = loc.latitude
                        longPressLon = loc.longitude
                        showAddMarker = true
                    }
                    true
                },
            )
        }
    ) {
        // Full-screen map
        MapViewComposable(
            modifier = Modifier.fillMaxSize(),
            showUserLocation = true,
            onMapReady = { map ->
                // Add long-press listener for adding markers
                map.addOnMapLongClickListener { point ->
                    longPressLat = point.latitude
                    longPressLon = point.longitude
                    showAddMarker = true
                    true
                }
                mapInstance = map
            },
        )

        // GPS waiting indicator - prominent and styled
        if (location == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        stateDescription = "Acquiring GPS signal"
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Acquiring GPS signal...",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        // Action buttons overlaid at bottom - large touch targets for gloves
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = CountersteerDesign.Spacing.lg, vertical = CountersteerDesign.Spacing.lg)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(CountersteerDesign.Spacing.targetGap),
        ) {
            RidingButton(
                text = "Explore",
                icon = Icons.Default.Explore,
                onClick = onNavigateToExplore,
                modifier = Modifier.weight(1f),
                iconContentDescription = "Explore routes",
            )
            RidingButton(
                text = "Search",
                icon = Icons.Default.Place,
                onClick = onNavigateToDestination,
                modifier = Modifier.weight(1f),
                iconContentDescription = "Search destination",
            )
        }
    }

    // Add marker dialog
    if (showAddMarker) {
        AddMarkerDialog(
            latitude = longPressLat,
            longitude = longPressLon,
            onConfirm = { type, note ->
                viewModel.addMarker(longPressLat, longPressLon, type, note)
                showAddMarker = false
            },
            onDismiss = { showAddMarker = false },
        )
    }
}

private fun drawMarkersOnMap(map: MapLibreMap, markers: List<RoadMarkerEntity>) {
    val style = map.style ?: return
    val sourceId = "markers-source"
    val layerId = "markers-layer"

    // Remove existing
    style.getLayer(layerId)?.let { style.removeLayer(it) }
    style.getSource(sourceId)?.let { style.removeSource(it) }

    if (markers.isEmpty()) return

    val features = markers.map { marker ->
        val feature = Feature.fromGeometry(
            Point.fromLngLat(marker.longitude, marker.latitude),
        )
        feature.addStringProperty("type", marker.type.name)
        feature
    }

    val source = GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features))
    style.addSource(source)

    val layer = CircleLayer(layerId, sourceId).withProperties(
        PropertyFactory.circleRadius(8f),
        PropertyFactory.circleColor(
            org.maplibre.android.style.expressions.Expression.match(
                org.maplibre.android.style.expressions.Expression.get("type"),
                org.maplibre.android.style.expressions.Expression.literal("SPEED_TRAP"),
                org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#CC6666")),
                org.maplibre.android.style.expressions.Expression.literal("HAZARD"),
                org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#F0C674")),
                org.maplibre.android.style.expressions.Expression.literal("PHOTO_SPOT"),
                org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#B5BD68")),
                org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#7EC8E3")), // default = CUSTOM
            ),
        ),
        PropertyFactory.circleStrokeWidth(2f),
        PropertyFactory.circleStrokeColor(android.graphics.Color.parseColor("#1A1A1A")),
        PropertyFactory.circleOpacity(0.9f),
    )
    style.addLayer(layer)
}
