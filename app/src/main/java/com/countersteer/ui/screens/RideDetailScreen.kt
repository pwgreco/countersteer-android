package com.countersteer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.ui.components.MapViewComposable
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.theme.CountersteerDesign
import org.maplibre.android.maps.MapLibreMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(
    rideId: Long,
    onNavigateBack: () -> Unit,
    onReride: (Long) -> Unit = {},
    onReturnTrip: (Double, Double) -> Unit = { _, _ -> },
    viewModel: RideSummaryViewModel = hiltViewModel(),
) {
    val ride by viewModel.ride.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    val coroutineScope = rememberCoroutineScope()

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

    Scaffold(
        topBar = {
            CountersteerTopBar(
                title = "Ride Detail",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
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
                    text = "Ride Details",
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.discardRide()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete", style = MaterialTheme.typography.titleMedium)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val savedId = viewModel.saveForReride()
                                if (savedId > 0) {
                                    onReride(savedId)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Re-ride", style = MaterialTheme.typography.titleMedium)
                    }

                    Button(
                        onClick = {
                            val firstPoint = trackPoints.firstOrNull()
                            if (firstPoint != null) {
                                onReturnTrip(firstPoint.latitude, firstPoint.longitude)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Navigate back to start")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Return", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
