package com.countersteer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.data.entity.RouteType
import com.countersteer.routing.model.Route
import com.countersteer.ui.components.MapViewComposable
import com.countersteer.ui.components.RidingButton
import com.countersteer.ui.theme.CurvyColor
import com.countersteer.ui.theme.QuickestColor
import com.countersteer.ui.theme.CountersteerDesign
import com.countersteer.ui.theme.ScenicColor
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteOptionsScreen(
    onNavigateBack: () -> Unit,
    onStartNavigation: () -> Unit,
    viewModel: RouteOptionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // Draw routes on map when they change
    LaunchedEffect(uiState.routes, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        if (uiState.routes.isEmpty()) return@LaunchedEffect
        drawRoutesOnMap(map, uiState.routes, uiState.selectedRouteType)
    }

    // Update highlighting when selection changes
    LaunchedEffect(uiState.selectedRouteType, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        if (uiState.routes.isEmpty()) return@LaunchedEffect
        drawRoutesOnMap(map, uiState.routes, uiState.selectedRouteType)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map background
        MapViewComposable(
            modifier = Modifier.fillMaxSize(),
            showUserLocation = true,
            onMapReady = { map -> mapInstance = map },
        )

        // Top bar overlay
        TopAppBar(
            title = { Text("Route Options") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            ),
            windowInsets = WindowInsets.statusBars,
        )

        // Bottom card area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Generating routes...",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            } else if (uiState.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.retry() }) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                // Nearby favourite routes
                if (uiState.nearbyFavourites.isNotEmpty()) {
                    Text(
                        text = "Favourite Routes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.nearbyFavourites) { favRoute ->
                            FavouriteRouteCard(
                                route = favRoute,
                                isSelected = favRoute.type == uiState.selectedRouteType,
                                onClick = { viewModel.selectRoute(favRoute.type) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Route cards
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.routes) { route ->
                        RouteCard(
                            route = route,
                            isSelected = route.type == uiState.selectedRouteType,
                            onClick = { viewModel.selectRoute(route.type) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Go button
                RidingButton(
                    text = "Start Navigation",
                    icon = Icons.Default.Navigation,
                    onClick = {
                        if (viewModel.prepareNavigation()) {
                            onStartNavigation()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    iconContentDescription = "Start navigation",
                )
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: Route,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val routeColor = when (route.type) {
        RouteType.CURVY -> CurvyColor
        RouteType.SCENIC -> ScenicColor
        RouteType.QUICKEST -> QuickestColor
    }

    val routeLabel = when (route.type) {
        RouteType.CURVY -> "Curvy"
        RouteType.SCENIC -> "Scenic"
        RouteType.QUICKEST -> "Quickest"
    }

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 1.dp,
        label = "border_width",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) routeColor else MaterialTheme.colorScheme.outline,
        label = "border_color",
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
        border = BorderStroke(animatedBorderWidth, animatedBorderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = null,
                    tint = routeColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = routeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = routeColor,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Distance
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "%.1f km".format(route.distanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Duration
            val durationMin = (route.durationWithTrafficSeconds ?: route.durationSeconds) / 60
            val hours = durationMin / 60
            val mins = durationMin % 60
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (route.durationWithTrafficSeconds != null) {
                    Text(
                        " (traffic)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Scores
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Terrain,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Curvy: ${route.curvatureScore.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Scenic: ${route.scenicScore.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (route.isOutAndBack) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Out & Back",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            if (route.hasUnpavedSegments) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Unpaved sections",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavouriteRouteCard(
    route: Route,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val routeColor = when (route.type) {
        RouteType.CURVY -> CurvyColor
        RouteType.SCENIC -> ScenicColor
        RouteType.QUICKEST -> QuickestColor
    }

    val routeLabel = when (route.type) {
        RouteType.CURVY -> "Curvy"
        RouteType.SCENIC -> "Scenic"
        RouteType.QUICKEST -> "Quickest"
    }

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 1.dp,
        label = "fav_border_width",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) routeColor else MaterialTheme.colorScheme.outline,
        label = "fav_border_color",
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
        border = BorderStroke(animatedBorderWidth, animatedBorderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favourite",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = routeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = routeColor,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "%.1f km".format(route.distanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val durationMin = (route.durationWithTrafficSeconds ?: route.durationSeconds) / 60
            val hours = durationMin / 60
            val mins = durationMin % 60
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun drawRoutesOnMap(
    map: MapLibreMap,
    routes: List<Route>,
    selectedType: RouteType,
) {
    val style = map.style ?: return

    // Remove existing route layers/sources
    for (routeType in RouteType.entries) {
        val sourceId = "route-source-${routeType.name}"
        val layerId = "route-layer-${routeType.name}"
        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }
    }

    // Draw non-selected routes first (behind), then selected route on top
    val sortedRoutes = routes.sortedBy { if (it.type == selectedType) 1 else 0 }

    val allPoints = mutableListOf<LatLng>()

    for (route in sortedRoutes) {
        val sourceId = "route-source-${route.type.name}"
        val layerId = "route-layer-${route.type.name}"

        val points = route.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (points.size < 2) continue

        val lineString = LineString.fromLngLats(points)
        val feature = Feature.fromGeometry(lineString)
        val source = GeoJsonSource(sourceId, feature)
        style.addSource(source)

        val color = when (route.type) {
            RouteType.CURVY -> CurvyColor
            RouteType.SCENIC -> ScenicColor
            RouteType.QUICKEST -> QuickestColor
        }

        val isSelected = route.type == selectedType
        val lineWidth = if (isSelected) 6f else 3f
        val opacity = if (isSelected) 1.0f else 0.5f

        val layer = LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(color.toArgb()),
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineOpacity(opacity),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
        )
        style.addLayer(layer)

        route.points.forEach { allPoints.add(LatLng(it.latitude, it.longitude)) }
    }

    // Fit map to show all routes
    if (allPoints.size >= 2) {
        try {
            val bounds = LatLngBounds.Builder()
            allPoints.forEach { bounds.include(it) }
            map.easeCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
                    bounds.build(),
                    100,
                ),
                1000,
            )
        } catch (_: Exception) {
            // Bounds calculation can fail with degenerate data
        }
    }
}
