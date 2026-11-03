package com.countersteer.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.alerts.AlertInfo
import com.countersteer.alerts.AlertManager
import com.countersteer.alerts.OverpassClient
import com.countersteer.data.entity.RouteType
import com.countersteer.routing.api.TomTomTrafficClient
import com.countersteer.data.location.LocationProvider
import com.countersteer.logging.RideLogger
import com.countersteer.logging.RideService
import com.countersteer.routing.RouteGenerator
import com.countersteer.routing.model.LatLng
import com.countersteer.routing.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationProvider: LocationProvider,
    private val turnByTurnEngine: TurnByTurnEngine,
    private val activeRouteHolder: ActiveRouteHolder,
    private val rideLogger: RideLogger,
    private val alertManager: AlertManager,
    private val overpassClient: OverpassClient,
    private val tomTomTrafficClient: TomTomTrafficClient,
    private val routeGenerator: RouteGenerator,
) : ViewModel() {

    private val _navigationState = MutableStateFlow(NavigationState())
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _autoEndRideId = MutableStateFlow(0L)
    val autoEndRideId: StateFlow<Long> = _autoEndRideId.asStateFlow()

    private val _gasStationStatus = MutableStateFlow("")
    val gasStationStatus: StateFlow<String> = _gasStationStatus.asStateFlow()

    val activeAlert: StateFlow<AlertInfo?> = alertManager.activeAlert

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var navigationJob: Job? = null

    private var lastTrafficFetchTime = 0L

    private var isRerouting = false
    private var lastRerouteTime = 0L

    companion object {
        private const val TRAFFIC_REFETCH_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val PACE_DEVIATION_THRESHOLD = 0.20 // 20%
        private const val REROUTE_COOLDOWN_MS = 15_000L // Don't reroute more than once per 15s
    }

    // Track which instruction distances have been spoken to avoid repeats
    private var lastSpokenManeuverIndex: Int = -1
    private var spokenAt200m: Boolean = false
    private var spokenAt50m: Boolean = false

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CANADA
                ttsReady = true
            }
        }
    }

    fun startNavigation(route: Route) {
        navigationJob?.cancel()
        turnByTurnEngine.setRoute(route)
        _navigationState.value = NavigationState(
            route = route,
            isNavigating = true,
        )
        lastSpokenManeuverIndex = -1
        spokenAt200m = false
        spokenAt50m = false

        // Start alert monitoring
        alertManager.startRide()

        // Switch to high-frequency location updates
        locationProvider.setNavigationMode(true)

        // Start ride recording service
        RideService.startRide(context, route.type)

        // Fetch OSM data along route in background
        viewModelScope.launch {
            try {
                overpassClient.fetchEnforcementAlongRoute(route.points)
            } catch (e: Exception) {
                Log.w("NavigationViewModel", "Failed to fetch enforcement data", e)
            }
        }

        // Fetch OSM speed limits along route in background
        viewModelScope.launch {
            try {
                val segments = overpassClient.fetchSpeedLimitsAlongRoute(route.points)
                if (segments.isNotEmpty()) {
                    Log.d("NavigationViewModel", "Loaded ${segments.size} OSM speed limit segments")
                    turnByTurnEngine.setOsmSpeedLimits(segments)
                }
            } catch (e: Exception) {
                Log.w("NavigationViewModel", "Failed to fetch OSM speed limits", e)
            }
        }

        // Observe location updates
        navigationJob = viewModelScope.launch {
            locationProvider.currentLocation
                .filterNotNull()
                .collect { location ->
                    val state = turnByTurnEngine.update(location)
                    if (state != null) {
                        _navigationState.value = state
                        handleVoiceInstructions(state)
                        // Update alert manager with current position
                        alertManager.updatePosition(state.currentLatitude, state.currentLongitude)
                        launch { maybeRefreshTrafficEta(state) }

                        // Auto-end trip near destination
                        if (state.distanceRemainingKm < 0.2 && state.isNavigating) {
                            // Within 200m of destination — auto-end
                            val rideId = stopNavigation()
                            _autoEndRideId.value = rideId
                        }

                        // Reroute when off-route instead of just showing a banner
                        if (state.isOffRoute && !isRerouting) {
                            val now = System.currentTimeMillis()
                            if (now - lastRerouteTime > REROUTE_COOLDOWN_MS) {
                                launch { reroute(state) }
                            }
                        }
                    }
                }
        }
    }

    fun dismissAlert() {
        alertManager.dismissAlert()
    }

    fun findNearbyGasStation() {
        viewModelScope.launch {
            _gasStationStatus.value = "Searching..."
            val state = _navigationState.value
            val route = state.route
            if (route == null) {
                _gasStationStatus.value = "No active route"
                delay(2000)
                _gasStationStatus.value = ""
                return@launch
            }

            try {
                val remainingPoints = route.points.drop(state.currentPointIndex)
                if (remainingPoints.size < 2) {
                    _gasStationStatus.value = "Near destination"
                    delay(2000)
                    _gasStationStatus.value = ""
                    return@launch
                }

                _gasStationStatus.value = "Searching nearby..."
                val stations = overpassClient.getFuelStationsNearRoute(remainingPoints)
                if (stations.isEmpty()) {
                    _gasStationStatus.value = "No stations found nearby"
                    delay(2000)
                    _gasStationStatus.value = ""
                    return@launch
                }

                val nearest = stations.minByOrNull { station ->
                    remainingPoints.minOf { point ->
                        haversineKm(
                            point.latitude, point.longitude,
                            station.latitude, station.longitude,
                        )
                    }
                } ?: return@launch

                _gasStationStatus.value = "Routing to ${nearest.name ?: "gas station"}..."

                val toStation = routeGenerator.generateDestinationRoutes(
                    origin = LatLng(state.currentLatitude, state.currentLongitude),
                    destination = LatLng(nearest.latitude, nearest.longitude),
                )
                val stationRoute = toStation.firstOrNull { it.type == RouteType.QUICKEST }
                    ?: toStation.firstOrNull()

                if (stationRoute == null) {
                    _gasStationStatus.value = "Route failed"
                    delay(2000)
                    _gasStationStatus.value = ""
                    return@launch
                }

                turnByTurnEngine.setRoute(stationRoute)
                activeRouteHolder.setActiveRoute(stationRoute)
                lastSpokenManeuverIndex = -1
                spokenAt200m = false
                spokenAt50m = false

                _gasStationStatus.value = ""
                Log.d("NavigationViewModel", "Rerouted to gas station: ${nearest.name ?: "unnamed"}")
            } catch (e: Exception) {
                Log.w("NavigationViewModel", "Failed to find gas station", e)
                _gasStationStatus.value = "Search failed"
                delay(2000)
                _gasStationStatus.value = ""
            }
        }
    }

    fun getActiveRoute(): Route? = activeRouteHolder.route

    suspend fun stopNavigation(): Long {
        navigationJob?.cancel()
        turnByTurnEngine.clear()
        _navigationState.value = NavigationState()
        locationProvider.setNavigationMode(false)
        activeRouteHolder.clear()
        alertManager.stopRide()
        tts?.stop()

        // Stop ride recording via the service (which manages its own logger lifecycle)
        RideService.stopRide(context)
        return rideLogger.progress.value.rideId
    }

    private suspend fun reroute(navState: NavigationState) {
        isRerouting = true
        lastRerouteTime = System.currentTimeMillis()
        try {
            val route = navState.route ?: return
            val currentLat = navState.currentLatitude
            val currentLon = navState.currentLongitude
            val points = route.points

            // Determine rejoin point — search ahead on the original route
            val minSkip = 50 // Skip at least 50 points ahead (~500m-1km depending on density)
            val searchStart = (navState.currentPointIndex + minSkip).coerceAtMost(points.size - 1)

            // If near end of route, fall back to routing to final destination
            if (searchStart >= points.size - 20 || navState.distanceRemainingKm < 0.5) {
                val dest = points.lastOrNull() ?: return
                val newRoutes = routeGenerator.generateDestinationRoutes(
                    origin = LatLng(currentLat, currentLon),
                    destination = LatLng(dest.latitude, dest.longitude),
                )
                val newRoute = newRoutes.firstOrNull { it.type == route.type }
                    ?: newRoutes.firstOrNull() ?: return
                turnByTurnEngine.setRoute(newRoute)
                activeRouteHolder.setActiveRoute(newRoute)
                lastSpokenManeuverIndex = -1
                spokenAt200m = false
                spokenAt50m = false
                return
            }

            // Find the closest route point that's ahead of us
            var bestIdx = searchStart
            var bestDist = Double.MAX_VALUE
            for (i in searchStart until points.size) {
                val dist = haversineKm(currentLat, currentLon, points[i].latitude, points[i].longitude)
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = i
                }
            }

            val rejoinPoint = points[bestIdx]

            // Generate route from current position to rejoin point
            val rerouteSegments = routeGenerator.generateDestinationRoutes(
                origin = LatLng(currentLat, currentLon),
                destination = LatLng(rejoinPoint.latitude, rejoinPoint.longitude),
            )
            val rerouteSegment = rerouteSegments.firstOrNull { it.type == RouteType.QUICKEST }
                ?: rerouteSegments.firstOrNull() ?: return

            // Splice: reroute segment points + remaining original route from rejoin index
            val remainingOriginalPoints = points.subList(bestIdx, points.size)
            val splicedPoints = rerouteSegment.points + remainingOriginalPoints

            // Calculate combined distance and duration
            val remainingOriginalDistance = route.distanceKm * (remainingOriginalPoints.size.toDouble() / points.size)
            val remainingOriginalDuration = route.durationSeconds * (remainingOriginalPoints.size.toDouble() / points.size)
            val splicedDistance = rerouteSegment.distanceKm + remainingOriginalDistance
            val splicedDuration = rerouteSegment.durationSeconds + remainingOriginalDuration.toLong()

            // Splice maneuvers: reroute maneuvers + remaining original maneuvers
            val remainingManeuvers = route.maneuvers.filter { maneuver ->
                val maneuverPointIdx = points.indexOfFirst { p ->
                    haversineKm(p.latitude, p.longitude, maneuver.latitude, maneuver.longitude) < 0.05
                }
                maneuverPointIdx >= bestIdx
            }
            val splicedManeuvers = rerouteSegment.maneuvers + remainingManeuvers

            val splicedRoute = route.copy(
                points = splicedPoints,
                maneuvers = splicedManeuvers,
                distanceKm = splicedDistance,
                durationSeconds = splicedDuration,
                durationWithTrafficSeconds = null, // Will be refreshed by traffic re-query
                polyline = "", // Polyline is no longer valid for spliced route
            )

            turnByTurnEngine.setRoute(splicedRoute)
            activeRouteHolder.setActiveRoute(splicedRoute)
            lastSpokenManeuverIndex = -1
            spokenAt200m = false
            spokenAt50m = false

            Log.d("NavigationViewModel", "Smart reroute: rejoining at point $bestIdx of ${points.size}, ${bestDist.let { "%.1f".format(it) }}km away")
        } catch (e: Exception) {
            Log.w("NavigationViewModel", "Smart reroute failed", e)
        } finally {
            isRerouting = false
        }
    }

    private suspend fun maybeRefreshTrafficEta(navState: NavigationState) {
        val route = navState.route ?: return
        val now = System.currentTimeMillis()
        val timeSinceLastFetch = now - lastTrafficFetchTime

        val totalDuration = route.durationWithTrafficSeconds ?: route.durationSeconds
        val totalDistance = route.distanceKm
        val predictedPaceKmPerSec = if (totalDuration > 0) totalDistance / totalDuration else 0.0

        val actualSpeedKmh = navState.currentSpeedKmh.toDouble()
        val actualPaceKmPerSec = if (actualSpeedKmh > 1.0) actualSpeedKmh / 3600.0 else 0.0

        val paceDeviation = if (predictedPaceKmPerSec > 0 && actualPaceKmPerSec > 0) {
            kotlin.math.abs(actualPaceKmPerSec - predictedPaceKmPerSec) / predictedPaceKmPerSec
        } else 0.0

        val shouldRefetch = timeSinceLastFetch >= TRAFFIC_REFETCH_INTERVAL_MS ||
            (paceDeviation > PACE_DEVIATION_THRESHOLD && timeSinceLastFetch >= 60_000L)

        if (!shouldRefetch) return

        try {
            val updatedDuration = fetchTrafficDurationForRemainingRoute(navState)
            if (updatedDuration != null) {
                turnByTurnEngine.updateTrafficDuration(updatedDuration)
                lastTrafficFetchTime = now
            }
        } catch (_: Exception) { }
    }

    private suspend fun fetchTrafficDurationForRemainingRoute(navState: NavigationState): Long? {
        val route = navState.route ?: return null
        val remainingPoints = route.points.drop(navState.currentPointIndex)
        if (remainingPoints.size < 2) return null

        val trafficFactor = tomTomTrafficClient.getTrafficFactor(remainingPoints) ?: return null
        val remainingBaseDuration = if (route.distanceKm > 0) {
            ((navState.distanceRemainingKm / route.distanceKm) * route.durationSeconds).toLong()
        } else return null

        return tomTomTrafficClient.adjustDurationForTraffic(
            durationSeconds = remainingBaseDuration,
            trafficFactor = trafficFactor,
            distanceKm = navState.distanceRemainingKm,
        )
    }

    private fun handleVoiceInstructions(state: NavigationState) {
        val maneuverIndex = state.currentManeuverIndex
        val distKm = state.distanceToNextManeuverKm
        val nextManeuver = state.nextManeuver ?: state.currentManeuver ?: return

        // Reset spoken flags when we advance to a new maneuver
        if (maneuverIndex != lastSpokenManeuverIndex) {
            lastSpokenManeuverIndex = maneuverIndex
            spokenAt200m = false
            spokenAt50m = false
        }

        // Speak at 200m
        if (!spokenAt200m && distKm <= 0.2 && distKm > 0.05) {
            spokenAt200m = true
            val distText = formatDistanceForSpeech(distKm)
            speak("In $distText, ${nextManeuver.instruction}")
        }

        // Speak at 50m
        if (!spokenAt50m && distKm <= 0.05) {
            spokenAt50m = true
            // Short instruction at close range
            val shortInstruction = nextManeuver.instruction
                .replace(Regex("^Continue.*"), "Continue")
            speak(shortInstruction)
        }
    }

    private fun formatDistanceForSpeech(distKm: Double): String {
        val meters = (distKm * 1000).toInt()
        return when {
            meters >= 1000 -> "%.1f kilometers".format(distKm)
            meters >= 100 -> "${(meters / 100) * 100} meters"
            else -> "$meters meters"
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "nav_${System.currentTimeMillis()}")
    }

    private fun haversineKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        locationProvider.setNavigationMode(false)
    }
}
