package com.countersteer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector = Icons.Default.Home,
) {
    data object Onboarding : Screen("onboarding", "Onboarding")
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object RideHistory : Screen("ride_history", "Rides", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object Explore : Screen("explore", "Explore")
    data object Destination : Screen("destination", "Search")
    data object RouteOptions : Screen(
        "route_options/{mode}?bearing={bearing}&distance={distance}&shape={shape}&destLat={destLat}&destLon={destLon}&destName={destName}&savedRouteId={savedRouteId}",
        "Route Options",
    ) {
        fun createExploreRoute(bearing: Float, distance: Float, shape: String = "ROUND_TRIP"): String =
            "route_options/explore?bearing=$bearing&distance=$distance&shape=$shape&destLat=0&destLon=0&destName=&savedRouteId=0"

        fun createDestinationRoute(destLat: Float, destLon: Float, destName: String): String =
            "route_options/destination?bearing=0&distance=0&shape=ROUND_TRIP&destLat=$destLat&destLon=$destLon&destName=${
                java.net.URLEncoder.encode(destName, "UTF-8")
            }&savedRouteId=0"

        fun createFavouriteRoute(savedRouteId: Long): String =
            "route_options/favourite?bearing=0&distance=0&shape=ROUND_TRIP&destLat=0&destLon=0&destName=&savedRouteId=$savedRouteId"
    }

    data object ActiveNavigation : Screen("active_navigation", "Navigation")

    data object RideSummary : Screen("ride_summary/{rideId}", "Ride Summary") {
        fun createRoute(rideId: Long): String = "ride_summary/$rideId"
    }

    data object RideDetail : Screen("ride_detail/{rideId}", "Ride Detail") {
        fun createRoute(rideId: Long): String = "ride_detail/$rideId"
    }

    data object OfflineMaps : Screen("offline_maps", "Offline Maps")
    data object BackupRestore : Screen("backup_restore", "Backup & Restore")
    data object ApiKeys : Screen("api_keys", "API Keys")
    data object RideStats : Screen("ride_stats", "Ride Statistics")
    data object Permissions : Screen("permissions", "Permissions")
}

val bottomNavScreens = listOf(Screen.Home, Screen.RideHistory, Screen.Settings)
