package com.countersteer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.countersteer.routing.model.RouteShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.components.RidingButton
import com.countersteer.ui.theme.CountersteerDesign

private data class CompassDirection(
    val label: String,
    val bearing: Double,
)

private val directions = listOf(
    CompassDirection("N", 0.0),
    CompassDirection("NE", 45.0),
    CompassDirection("E", 90.0),
    CompassDirection("SE", 135.0),
    CompassDirection("S", 180.0),
    CompassDirection("SW", 225.0),
    CompassDirection("W", 270.0),
    CompassDirection("NW", 315.0),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onNavigateBack: () -> Unit,
    onRoutesReady: (bearing: Double, distance: Double, shape: RouteShape) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var hasNavigated by remember { mutableStateOf(false) }

    // When routes are loaded, navigate to route options (once only)
    LaunchedEffect(uiState.routes) {
        if (uiState.routes.isNotEmpty() && !hasNavigated) {
            hasNavigated = true
            onRoutesReady(uiState.selectedBearing, uiState.distanceKm, uiState.routeShape)
            viewModel.clearRoutes()
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            CountersteerTopBar(
                title = "Explore",
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            // Find Routes button pinned to bottom — always visible
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                RidingButton(
                    text = if (uiState.isLoading) "Finding routes..." else "Find Routes",
                    icon = Icons.Default.Search,
                    onClick = { viewModel.findRoutes() },
                    modifier = Modifier.fillMaxWidth(),
                    iconContentDescription = "Find routes",
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pick a direction",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Compass rose
            CompassRose(
                selectedBearing = uiState.selectedBearing,
                onBearingSelected = { viewModel.setBearing(it) },
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Route shape toggle
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedButton(
                    selected = uiState.routeShape == RouteShape.ROUND_TRIP,
                    onClick = { viewModel.setRouteShape(RouteShape.ROUND_TRIP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Round Trip")
                }
                SegmentedButton(
                    selected = uiState.routeShape == RouteShape.OUT_AND_BACK,
                    onClick = { viewModel.setRouteShape(RouteShape.OUT_AND_BACK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Out & Back")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Distance slider
            Text(
                text = "Round trip distance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "${uiState.distanceKm.toInt()} km",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Slider(
                value = uiState.distanceKm.toFloat(),
                onValueChange = { viewModel.setDistance(it.toDouble()) },
                valueRange = 20f..500f,
                steps = 23,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("20 km", style = MaterialTheme.typography.bodySmall)
                Text("500 km", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CompassRose(
    selectedBearing: Double,
    onBearingSelected: (Double) -> Unit,
) {
    // Layout: 3 rows
    // Row 1: NW  N  NE
    // Row 2: W   *  E
    // Row 3: SW  S  SE
    val grid = listOf(
        listOf("NW", "N", "NE"),
        listOf("W", "", "E"),
        listOf("SW", "S", "SE"),
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (row in grid) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (label in row) {
                    if (label.isEmpty()) {
                        // Center compass marker
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "\u2316",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val dir = directions.first { it.label == label }
                        val isSelected = selectedBearing == dir.bearing
                        val animatedContainerColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            label = "compass_color",
                        )

                        FilledTonalButton(
                            onClick = { onBearingSelected(dir.bearing) },
                            modifier = Modifier
                                .size(72.dp)
                                .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                            shape = CircleShape,
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = animatedContainerColor,
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
