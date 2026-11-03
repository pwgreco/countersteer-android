package com.countersteer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.data.entity.RideEntity
import com.countersteer.data.entity.RouteType
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.components.StateContainer
import com.countersteer.ui.components.UiState
import com.countersteer.ui.theme.CurvyColor
import com.countersteer.ui.theme.CountersteerDesign
import com.countersteer.ui.theme.QuickestColor
import com.countersteer.ui.theme.ScenicColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(
    onRideClick: (Long) -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    viewModel: RideHistoryViewModel = hiltViewModel(),
) {
    val rides by viewModel.rides.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            CountersteerTopBar(
                title = "Rides",
                actions = {
                    IconButton(
                        onClick = onNavigateToStats,
                        modifier = Modifier.size(CountersteerDesign.TouchTarget.topBarIcon),
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Statistics",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        StateContainer(
            state = if (rides.isEmpty()) UiState.Empty(
                icon = Icons.Default.TwoWheeler,
                title = "No rides yet",
                subtitle = "Hit the road and your rides will appear here",
            ) else UiState.Success,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rides, key = { it.id }, contentType = { "ride" }) { ride ->
                    RideListItem(
                        ride = ride,
                        onClick = { onRideClick(ride.id) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RideListItem(
    ride: RideEntity,
    onClick: () -> Unit,
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormat.format(Date(ride.startedAt)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = "%.1f km".format(ride.distanceKm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formatDurationHms(ride.durationSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Route type badge
            val badgeColor = when (ride.routeType) {
                RouteType.CURVY -> CurvyColor
                RouteType.SCENIC -> ScenicColor
                RouteType.QUICKEST -> QuickestColor
            }
            val badgeText = when (ride.routeType) {
                RouteType.CURVY -> "Curvy"
                RouteType.SCENIC -> "Scenic"
                RouteType.QUICKEST -> "Quick"
            }
            Box(
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                )
            }
        }
    }
}
