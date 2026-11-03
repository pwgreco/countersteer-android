package com.countersteer.ui.screens

import android.view.Gravity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.data.entity.RouteType
import com.countersteer.navigation.ManeuverIcon
import com.countersteer.navigation.NavigationState
import com.countersteer.navigation.NavigationViewModel
import com.countersteer.navigation.maneuverTypeToIcon
import com.countersteer.routing.model.Route
import com.countersteer.ui.components.AlertBanner
import com.countersteer.ui.components.MapViewComposable
import com.countersteer.ui.theme.CurvyColor
import com.countersteer.ui.theme.QuickestColor
import com.countersteer.ui.theme.ScenicColor
import com.countersteer.ui.components.SpeedDisplay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun ActiveNavigationScreen(
    route: Route,
    onEndNavigation: (rideId: Long) -> Unit,
    viewModel: NavigationViewModel = hiltViewModel(),
) {
    val navState by viewModel.navigationState.collectAsState()
    val activeAlert by viewModel.activeAlert.collectAsState()
    val autoEndRideId by viewModel.autoEndRideId.collectAsState()
    val gasStationStatus by viewModel.gasStationStatus.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapCentered by remember { mutableStateOf(true) }

    LaunchedEffect(autoEndRideId) {
        if (autoEndRideId > 0) {
            onEndNavigation(autoEndRideId)
        }
    }

    // Start navigation when screen enters composition
    LaunchedEffect(route) {
        viewModel.startNavigation(route)
    }

    // Draw route on map when ready
    LaunchedEffect(mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        drawNavigationRoute(map, route)
    }

    // Redraw route on map when it changes (e.g., after reroute)
    LaunchedEffect(navState.route) {
        val map = mapInstance ?: return@LaunchedEffect
        val currentRoute = navState.route ?: return@LaunchedEffect
        drawNavigationRoute(map, currentRoute)
    }

    // Update camera to follow position
    LaunchedEffect(navState.currentLatitude, navState.currentLongitude, navState.currentBearing) {
        val map = mapInstance ?: return@LaunchedEffect
        if (navState.currentLatitude == 0.0 && navState.currentLongitude == 0.0) return@LaunchedEffect
        if (!isMapCentered) return@LaunchedEffect  // Don't auto-track if user panned away

        // Auto-zoom based on speed: zoom out at higher speeds
        val zoom = when {
            navState.currentSpeedKmh > 100 -> 13.0
            navState.currentSpeedKmh > 60 -> 14.0
            navState.currentSpeedKmh > 30 -> 15.0
            else -> 16.0
        }

        map.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(navState.currentLatitude, navState.currentLongitude))
                    .zoom(zoom)
                    .bearing(navState.currentBearing.toDouble())
                    .tilt(60.0) // 3D perspective for navigation
                    .build(),
            ),
            500, // smooth animation
        )
    }

    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Map layer (center)
        MapViewComposable(
            modifier = Modifier.fillMaxSize(),
            showUserLocation = true,
            onMapReady = { map ->
                // Enable heading-up tracking
                try {
                    map.locationComponent.apply {
                        cameraMode = CameraMode.TRACKING_GPS_NORTH
                        renderMode = RenderMode.GPS
                    }
                } catch (_: Exception) {
                    // Location component may not be ready
                }
                // Move attribution out of the way
                map.uiSettings.apply {
                    isLogoEnabled = false
                    isAttributionEnabled = false
                    logoGravity = Gravity.TOP or Gravity.START
                }
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        isMapCentered = false
                    }
                }
                mapInstance = map
            },
        )

        // Top instruction bar
        InstructionBar(
            navState = navState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        // Alert banner (below instruction bar)
        AlertBanner(
            alertInfo = activeAlert,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp),
            onDismiss = { viewModel.dismissAlert() },
        )

        // Bottom info strip
        BottomInfoStrip(
            navState = navState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )

        // Recalculating overlay - shown while off-route (reroute in progress)
        if (navState.isOffRoute) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Assertive
                        stateDescription = "Recalculating route"
                    },
            ) {
                Text(
                    text = "Recalculating...",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        // Gas station search status
        if (gasStationStatus.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Text(
                    text = gasStationStatus,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        // Bottom-right action buttons — gas on top, end trip below
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Gas station FAB
            FloatingActionButton(
                onClick = { viewModel.findNearbyGasStation() },
                modifier = Modifier.size(com.countersteer.ui.theme.CountersteerDesign.TouchTarget.standard),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.LocalGasStation,
                    contentDescription = "Find gas station",
                    modifier = Modifier.size(com.countersteer.ui.theme.CountersteerDesign.IconSize.xl),
                )
            }

            // End navigation FAB
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val rideId = viewModel.stopNavigation()
                        onEndNavigation(rideId)
                    }
                },
                modifier = Modifier.size(com.countersteer.ui.theme.CountersteerDesign.TouchTarget.standard),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "End trip",
                    modifier = Modifier.size(com.countersteer.ui.theme.CountersteerDesign.IconSize.xl),
                )
            }
        }

        // Recenter FAB - appears when the user has manually panned the map
        if (!isMapCentered) {
            FloatingActionButton(
                onClick = {
                    isMapCentered = true
                    mapInstance?.let { map ->
                        map.easeCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(navState.currentLatitude, navState.currentLongitude))
                                    .zoom(15.0)
                                    .bearing(navState.currentBearing.toDouble())
                                    .tilt(60.0)
                                    .build(),
                            ),
                            300,
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                        end = 16.dp,
                    ),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter map",
                    modifier = Modifier.size(com.countersteer.ui.theme.CountersteerDesign.IconSize.lg),
                )
            }
        }
    }
}

@Composable
private fun InstructionBar(
    navState: NavigationState,
    modifier: Modifier = Modifier,
) {
    val maneuver = navState.nextManeuver ?: navState.currentManeuver

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.87f))
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = 16.dp,
            )
            .animateContentSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Turn arrow icon - large for glanceability
            val icon = maneuver?.let { maneuverTypeToIcon(it.type) } ?: ManeuverIcon.STRAIGHT
            Icon(
                imageVector = maneuverIconToVector(icon),
                contentDescription = maneuver?.instruction ?: "Continue straight",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Instruction text and distance
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            ) {
                // Distance to next maneuver - large and bold
                Text(
                    text = formatDistance(navState.distanceToNextManeuverKm),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displaySmall,
                )
                // Instruction
                Text(
                    text = maneuver?.instruction ?: "Follow route",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BottomInfoStrip(
    navState: NavigationState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.87f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            // Left: Speed + Speed limit - THE most important readout
            SpeedDisplay(
                currentSpeedKmh = navState.currentSpeedKmh,
                speedLimitKmh = navState.currentSpeedLimitKmh,
            )

            // Center: ETA + arrival time
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatEta(navState.etaSeconds),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = formatArrivalTime(navState.etaSeconds),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // Right: Distance remaining
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatDistance(navState.distanceRemainingKm),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "remaining",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun maneuverIconToVector(icon: ManeuverIcon): ImageVector = when (icon) {
    ManeuverIcon.STRAIGHT -> Icons.Default.Straight
    ManeuverIcon.TURN_LEFT -> Icons.Default.TurnLeft
    ManeuverIcon.TURN_RIGHT -> Icons.Default.TurnRight
    ManeuverIcon.SHARP_LEFT -> Icons.Default.TurnSharpLeft
    ManeuverIcon.SHARP_RIGHT -> Icons.Default.TurnSharpRight
    ManeuverIcon.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    ManeuverIcon.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    ManeuverIcon.U_TURN_LEFT -> Icons.Default.UTurnLeft
    ManeuverIcon.U_TURN_RIGHT -> Icons.Default.UTurnRight
    ManeuverIcon.ARRIVE -> Icons.Default.Flag
    ManeuverIcon.DEPART -> Icons.Default.Navigation
    ManeuverIcon.ROUNDABOUT -> Icons.Default.SyncAlt
    ManeuverIcon.MERGE -> Icons.AutoMirrored.Filled.MergeType
    ManeuverIcon.RAMP -> Icons.Default.RampRight
    ManeuverIcon.UNKNOWN -> Icons.Default.Straight
}

private fun formatDistance(distanceKm: Double): String {
    return when {
        distanceKm < 0.01 -> "Now"
        distanceKm < 1.0 -> "${(distanceKm * 1000).toInt()} m"
        else -> "%.1f km".format(distanceKm)
    }
}

private fun formatEta(etaSeconds: Long): String {
    val clamped = etaSeconds.coerceAtLeast(60)
    val hours = clamped / 3600
    val mins = (clamped % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins} min"
    }
}

private fun formatArrivalTime(etaSeconds: Long): String {
    val arrival = java.time.LocalTime.now().plusSeconds(etaSeconds)
    return arrival.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
}

private fun drawNavigationRoute(map: MapLibreMap, route: Route) {
    val style = map.style ?: return
    val sourceId = "nav-route-source"
    val layerId = "nav-route-layer"

    // Remove existing
    style.getLayer(layerId)?.let { style.removeLayer(it) }
    style.getSource(sourceId)?.let { style.removeSource(it) }

    val points = route.points.map { Point.fromLngLat(it.longitude, it.latitude) }
    if (points.size < 2) return

    val lineString = LineString.fromLngLats(points)
    val feature = Feature.fromGeometry(lineString)
    val source = GeoJsonSource(sourceId, feature)
    style.addSource(source)

    val color = when (route.type) {
        RouteType.CURVY -> CurvyColor
        RouteType.SCENIC -> ScenicColor
        RouteType.QUICKEST -> QuickestColor
    }

    val layer = LineLayer(layerId, sourceId).withProperties(
        PropertyFactory.lineColor(color.toArgb()),
        PropertyFactory.lineWidth(6f),
        PropertyFactory.lineOpacity(1.0f),
        PropertyFactory.lineCap("round"),
        PropertyFactory.lineJoin("round"),
    )
    style.addLayer(layer)
}
