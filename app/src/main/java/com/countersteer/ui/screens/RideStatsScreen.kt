package com.countersteer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.components.StateContainer
import com.countersteer.ui.components.UiState
import com.countersteer.ui.theme.CountersteerDesign

private val tabTitles = listOf("Week", "Month", "Year", "All Time")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideStatsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: RideStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CountersteerTopBar(title = "Ride Statistics", onNavigateBack = onNavigateBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        modifier = Modifier.defaultMinSize(minHeight = CountersteerDesign.TouchTarget.input),
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                    )
                }
            }

            if (state.isLoading) {
                StateContainer(state = UiState.Loading) {}
            } else {
                val stats = when (selectedTab) {
                    0 -> state.weekStats
                    1 -> state.monthStats
                    2 -> state.yearStats
                    3 -> state.allTimeStats
                    else -> null
                }
                val emptyMessage = when (selectedTab) {
                    0 -> "No rides this week"
                    1 -> "No rides this month"
                    2 -> "No rides this year"
                    else -> "No rides yet"
                }

                if (stats == null) {
                    StateContainer(
                        state = UiState.Empty(
                            icon = Icons.Default.TwoWheeler,
                            title = emptyMessage,
                        ),
                    ) {}
                } else {
                    StatsGrid(stats = stats)
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: PeriodStats) {
    val hours = stats.totalDurationSeconds / 3600
    val minutes = (stats.totalDurationSeconds % 3600) / 60
    val durationText = "${hours}h ${minutes}m"

    val items = listOf(
        StatItem("Total Rides", stats.totalRides.toString(), ""),
        StatItem("Total Distance", "%.1f".format(stats.totalDistanceKm), "km"),
        StatItem("Total Ride Time", durationText, ""),
        StatItem("Avg Distance", "%.1f".format(stats.avgDistanceKm), "km"),
        StatItem("Avg Speed", "%.1f".format(stats.avgSpeedKmh), "km/h"),
        StatItem("Top Speed", "%.1f".format(stats.topSpeedKmh), "km/h"),
        StatItem("Elevation Gain", "%.0f".format(stats.totalElevationGainM), "m"),
        StatItem("Avg Curvature", "%.0f".format(stats.avgCurvatureScore), "/ 100"),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { index, _ -> index }) { _, item ->
            StatCard(item = item)
        }
    }
}

private data class StatItem(
    val label: String,
    val value: String,
    val unit: String,
)

@Composable
private fun StatCard(item: StatItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            if (item.unit.isNotEmpty()) {
                Text(
                    text = item.unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
