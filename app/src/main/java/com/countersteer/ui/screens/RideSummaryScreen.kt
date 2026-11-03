package com.countersteer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.data.entity.RideEntity
import com.countersteer.data.entity.RideTrackPointEntity
import com.countersteer.ui.components.MapViewComposable
import com.countersteer.ui.components.RidingButton
import com.countersteer.ui.theme.CountersteerDesign
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun RideSummaryScreen(
    rideId: Long,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    viewModel: RideSummaryViewModel = hiltViewModel(),
) {
    val ride by viewModel.ride.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var showFavouriteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(rideId) {
        viewModel.loadRide(rideId)
    }

    val trackColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(mapInstance, trackPoints) {
        val map = mapInstance ?: return@LaunchedEffect
        if (trackPoints.size >= 2) {
            drawTrack(map, trackPoints, trackColor)
        }
    }

    val currentRide = ride ?: return

    if (showFavouriteDialog) {
        var favouriteName by remember {
            mutableStateOf("${ride?.routeType?.name?.lowercase()?.replaceFirstChar { it.uppercase() }} Route")
        }
        AlertDialog(
            onDismissRequest = { showFavouriteDialog = false },
            title = { Text("Save as Favourite") },
            text = {
                OutlinedTextField(
                    value = favouriteName,
                    onValueChange = { favouriteName = it },
                    label = { Text("Route name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = CountersteerDesign.TouchTarget.input),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveAsFavourite(favouriteName)
                        showFavouriteDialog = false
                        onSave()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFavouriteDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                ) { Text("Cancel") }
            },
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Map showing GPS track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            ) {
                MapViewComposable(
                    modifier = Modifier.fillMaxSize(),
                    showUserLocation = false,
                    onMapReady = { map -> mapInstance = map },
                )
            }

            // Stats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    text = "Ride Complete",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(16.dp))

                StatsGrid(currentRide)

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons - large for glove use
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CountersteerDesign.Spacing.targetGap),
                ) {
                    RidingButton(
                        text = "Discard",
                        icon = Icons.Default.Delete,
                        onClick = {
                            viewModel.discardRide()
                            onDiscard()
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )

                    RidingButton(
                        text = "Favourite",
                        icon = Icons.Default.Star,
                        onClick = { showFavouriteDialog = true },
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        iconContentDescription = "Save as favourite route",
                    )

                    RidingButton(
                        text = "Save Ride",
                        icon = Icons.Default.Save,
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatsGrid(ride: RideEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = "Distance",
                value = "%.1f km".format(ride.distanceKm),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Duration",
                value = formatDurationHms(ride.durationSeconds),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = "Avg Speed",
                value = "%.1f km/h".format(ride.avgSpeedKmh),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Max Speed",
                value = "%.1f km/h".format(ride.maxSpeedKmh),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = "Elevation Gain",
                value = "%.0f m".format(ride.elevationGainM),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Curvature",
                value = "%.0f / 100".format(ride.curvatureScore),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

internal fun formatDurationHms(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

internal fun drawTrack(map: MapLibreMap, points: List<RideTrackPointEntity>, lineColor: Color) {
    val style = map.style ?: return
    val sourceId = "ride-track-source"
    val layerId = "ride-track-layer"

    style.getLayer(layerId)?.let { style.removeLayer(it) }
    style.getSource(sourceId)?.let { style.removeSource(it) }

    val geoPoints = points.map { Point.fromLngLat(it.longitude, it.latitude) }
    if (geoPoints.size < 2) return

    val lineString = LineString.fromLngLats(geoPoints)
    val feature = Feature.fromGeometry(lineString)
    val source = GeoJsonSource(sourceId, feature)
    style.addSource(source)

    val layer = LineLayer(layerId, sourceId).withProperties(
        PropertyFactory.lineColor(lineColor.toArgb()),
        PropertyFactory.lineWidth(5f),
        PropertyFactory.lineOpacity(1.0f),
        PropertyFactory.lineCap("round"),
        PropertyFactory.lineJoin("round"),
    )
    style.addLayer(layer)

    // Fit bounds
    try {
        val boundsBuilder = LatLngBounds.Builder()
        points.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
        map.easeCamera(
            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80),
            1000,
        )
    } catch (_: Exception) {
        // bounds can fight with degenerate data
    }
}
