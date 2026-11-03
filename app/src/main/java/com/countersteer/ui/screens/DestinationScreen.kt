package com.countersteer.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.countersteer.routing.api.GeocodingResult
import com.countersteer.routing.model.LatLng
import com.countersteer.ui.components.CountersteerTopBar
import com.countersteer.ui.theme.CountersteerDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationScreen(
    onNavigateBack: () -> Unit,
    onDestinationSelected: (lat: Double, lon: Double, name: String) -> Unit,
    onFavouriteRouteSelected: (Long) -> Unit,
    viewModel: DestinationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentRoutes by viewModel.recentRoutes.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favouriteRoutes by viewModel.favouriteRoutes.collectAsState()

    var showSaveFavoriteDialog by remember { mutableStateOf<GeocodingResult?>(null) }

    showSaveFavoriteDialog?.let { result ->
        SaveFavoriteDialog(
            defaultName = result.displayName,
            onDismiss = { showSaveFavoriteDialog = null },
            onConfirm = { name ->
                viewModel.saveFavorite(name, result.latLng)
                showSaveFavoriteDialog = null
            },
        )
    }

    Scaffold(
        topBar = {
            CountersteerTopBar(
                title = "Search",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        // Search bar
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearch = { },
                    expanded = false,
                    onExpandedChange = { },
                    placeholder = { Text("Search for a destination...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (uiState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                )
            },
            expanded = false,
            onExpandedChange = { },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.input)
                .padding(horizontal = 16.dp),
        ) { }

        // Search error message
        uiState.searchError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            // Search results
            if (uiState.searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Search Results",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                items(uiState.searchResults, key = { result -> result.latLng.latitude.toString() + result.latLng.longitude.toString() }) { result ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = result.displayName,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { showSaveFavoriteDialog = result }) {
                                Icon(
                                    imageVector = Icons.Outlined.StarOutline,
                                    contentDescription = "Save as favourite",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
                            .clickable {
                                onDestinationSelected(
                                    result.latLng.latitude,
                                    result.latLng.longitude,
                                    result.displayName,
                                )
                            },
                    )
                    HorizontalDivider()
                }
            }

            // Favourite locations
            if (favorites.isNotEmpty()) {
                item {
                    Text(
                        text = "Favourites",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                items(favorites, key = { fav -> fav.id }) { fav ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = fav.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "%.4f, %.4f".format(fav.latitude, fav.longitude),
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteFavorite(fav.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove favourite",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
                            .clickable {
                                onDestinationSelected(
                                    fav.latitude,
                                    fav.longitude,
                                    fav.name,
                                )
                            },
                    )
                    HorizontalDivider()
                }
            }

            // Favourite routes
            if (favouriteRoutes.isNotEmpty()) {
                item {
                    Text(
                        text = "Favourite Routes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(favouriteRoutes, key = { it.id }) { route ->
                    ListItem(
                        headlineContent = { Text(route.name) },
                        supportingContent = {
                            Text("${route.routeType.name.lowercase().replaceFirstChar { it.uppercase() }} • ${"%.1f".format(route.distanceKm)} km")
                        },
                        leadingContent = {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
                            .clickable { onFavouriteRouteSelected(route.id) },
                    )
                }
                item { HorizontalDivider() }
            }

            // Recent destinations
            val recentWithDest = recentRoutes.filter {
                it.destinationLat != null && it.destinationLon != null
            }
            if (recentWithDest.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Destinations",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                items(recentWithDest.take(10), key = { route -> route.id }) { route ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = route.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "%.1f km".format(route.distanceKm),
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = CountersteerDesign.TouchTarget.standard)
                            .clickable {
                                onDestinationSelected(
                                    route.destinationLat!!,
                                    route.destinationLon!!,
                                    route.name,
                                )
                            },
                    )
                    HorizontalDivider()
                }
            }

            // Empty state
            if (uiState.searchResults.isEmpty() && recentWithDest.isEmpty() && favorites.isEmpty() && favouriteRoutes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Search for a destination above",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SaveFavoriteDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName.take(50)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Favourite") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(100) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
