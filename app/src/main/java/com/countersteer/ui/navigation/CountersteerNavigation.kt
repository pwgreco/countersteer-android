package com.countersteer.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.countersteer.logging.RideService
import com.countersteer.navigation.ActiveRouteHolder
import com.countersteer.onboarding.OnboardingScreen
import com.countersteer.ui.screens.ActiveNavigationScreen
import com.countersteer.ui.screens.DestinationScreen
import com.countersteer.ui.screens.ExploreScreen
import com.countersteer.ui.screens.HomeScreen
import com.countersteer.ui.screens.OfflineMapsScreen
import com.countersteer.ui.screens.RideDetailScreen
import com.countersteer.ui.screens.RideHistoryScreen
import com.countersteer.ui.screens.RideStatsScreen
import com.countersteer.ui.screens.RideSummaryScreen
import com.countersteer.ui.screens.RouteOptionsScreen
import com.countersteer.ui.screens.ApiKeysScreen
import com.countersteer.ui.screens.BackupRestoreScreen
import com.countersteer.ui.screens.PermissionsScreen
import com.countersteer.ui.screens.SettingsScreen
import androidx.compose.foundation.layout.padding
import com.countersteer.ui.theme.CountersteerTheme

@Composable
fun CountersteerApp(
    viewModel: MainNavViewModel = hiltViewModel(),
) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()
    if (onboardingComplete == null) return

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isOnboarding = currentDestination?.route == Screen.Onboarding.route
    val isActiveNavigation = currentDestination?.route == Screen.ActiveNavigation.route
    val isRideSummary = currentDestination?.route == Screen.RideSummary.route

    val startDestination = if (onboardingComplete == true) {
        Screen.Home.route
    } else {
        Screen.Onboarding.route
    }

    CountersteerTheme(isRiding = isActiveNavigation) {
        Scaffold(
            bottomBar = {
                if (!isOnboarding && !isActiveNavigation && !isRideSummary) {
                    NavigationBar {
                        bottomNavScreens.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(screen.route, null)
                                } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(300),
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(300),
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(300),
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(300),
                    )
                },
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        onOnboardingComplete = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToExplore = {
                            navController.navigate(Screen.Explore.route)
                        },
                        onNavigateToDestination = {
                            navController.navigate(Screen.Destination.route)
                        },
                    )
                }
                composable(Screen.RideHistory.route) {
                    RideHistoryScreen(
                        onRideClick = { rideId ->
                            navController.navigate(Screen.RideDetail.createRoute(rideId))
                        },
                        onNavigateToStats = {
                            navController.navigate(Screen.RideStats.route)
                        },
                    )
                }
                composable(Screen.RideStats.route) {
                    RideStatsScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateToOfflineMaps = {
                            navController.navigate(Screen.OfflineMaps.route)
                        },
                        onNavigateToBackupRestore = {
                            navController.navigate(Screen.BackupRestore.route)
                        },
                        onNavigateToApiKeys = {
                            navController.navigate(Screen.ApiKeys.route)
                        },
                        onNavigateToPermissions = {
                            navController.navigate(Screen.Permissions.route)
                        },
                    )
                }
                composable(Screen.BackupRestore.route) {
                    BackupRestoreScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
                composable(Screen.ApiKeys.route) {
                    ApiKeysScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
                composable(Screen.OfflineMaps.route) {
                    OfflineMapsScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
                composable(Screen.Permissions.route) {
                    PermissionsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Explore.route) {
                    ExploreScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onRoutesReady = { bearing, distance, shape ->
                            navController.navigate(
                                Screen.RouteOptions.createExploreRoute(
                                    bearing.toFloat(),
                                    distance.toFloat(),
                                    shape.name,
                                ),
                            ) {
                                popUpTo(Screen.Explore.route) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Screen.Destination.route) {
                    DestinationScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onDestinationSelected = { lat, lon, name ->
                            navController.navigate(
                                Screen.RouteOptions.createDestinationRoute(
                                    lat.toFloat(),
                                    lon.toFloat(),
                                    name,
                                ),
                            ) {
                                popUpTo(Screen.Destination.route) { inclusive = true }
                            }
                        },
                        onFavouriteRouteSelected = { savedRouteId ->
                            navController.navigate(Screen.RouteOptions.createFavouriteRoute(savedRouteId)) {
                                popUpTo(Screen.Destination.route) { inclusive = true }
                            }
                        },
                    )
                }
                composable(
                    route = Screen.RouteOptions.route,
                    arguments = listOf(
                        navArgument("mode") { type = NavType.StringType },
                        navArgument("bearing") {
                            type = NavType.FloatType
                            defaultValue = 0f
                        },
                        navArgument("distance") {
                            type = NavType.FloatType
                            defaultValue = 100f
                        },
                        navArgument("shape") {
                            type = NavType.StringType
                            defaultValue = "ROUND_TRIP"
                        },
                        navArgument("destLat") {
                            type = NavType.FloatType
                            defaultValue = 0f
                        },
                        navArgument("destLon") {
                            type = NavType.FloatType
                            defaultValue = 0f
                        },
                        navArgument("destName") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("savedRouteId") {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                    ),
                ) {
                    RouteOptionsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onStartNavigation = {
                            navController.navigate(Screen.ActiveNavigation.route) {
                                // Keep RouteOptions in back stack so user can return
                            }
                        },
                    )
                }
                composable(
                    Screen.ActiveNavigation.route,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { fadeOut(tween(150)) },
                ) {
                    val navViewModel: com.countersteer.navigation.NavigationViewModel = hiltViewModel()
                    val route = navViewModel.getActiveRoute()
                    if (route != null) {
                        ActiveNavigationScreen(
                            route = route,
                            onEndNavigation = { rideId ->
                                if (rideId > 0) {
                                    navController.navigate(Screen.RideSummary.createRoute(rideId)) {
                                        popUpTo(Screen.Home.route) { inclusive = false }
                                    }
                                } else {
                                    navController.popBackStack(Screen.Home.route, false)
                                }
                            },
                            viewModel = navViewModel,
                        )
                    } else {
                        // No route available, go back
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            navController.popBackStack()
                        }
                    }
                }
                composable(
                    route = Screen.RideSummary.route,
                    arguments = listOf(
                        navArgument("rideId") { type = NavType.LongType },
                    ),
                    enterTransition = { fadeIn(tween(300)) },
                ) { backStackEntry ->
                    val rideId = backStackEntry.arguments?.getLong("rideId") ?: return@composable
                    RideSummaryScreen(
                        rideId = rideId,
                        onSave = {
                            navController.popBackStack(Screen.Home.route, false)
                        },
                        onDiscard = {
                            navController.popBackStack(Screen.Home.route, false)
                        },
                    )
                }
                composable(
                    route = Screen.RideDetail.route,
                    arguments = listOf(
                        navArgument("rideId") { type = NavType.LongType },
                    ),
                ) { backStackEntry ->
                    val rideId = backStackEntry.arguments?.getLong("rideId") ?: return@composable
                    RideDetailScreen(
                        rideId = rideId,
                        onNavigateBack = { navController.popBackStack() },
                        onReride = { savedRouteId ->
                            navController.navigate(Screen.RouteOptions.createFavouriteRoute(savedRouteId))
                        },
                        onReturnTrip = { lat, lon ->
                            navController.navigate(
                                Screen.RouteOptions.createDestinationRoute(lat.toFloat(), lon.toFloat(), "Return to start")
                            )
                        },
                    )
                }
            }
        }
    }
}
