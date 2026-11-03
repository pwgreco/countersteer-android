package com.countersteer.navigation

import android.location.Location
import com.countersteer.alerts.SpeedLimitSegment
import com.countersteer.routing.model.LatLng
import com.countersteer.routing.model.Maneuver
import com.countersteer.routing.model.Route
import com.countersteer.routing.model.SpeedLimit
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core navigation engine that tracks position along a route and generates
 * turn-by-turn instruction data.
 */
class TurnByTurnEngine @Inject constructor() {

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        private const val OFF_ROUTE_THRESHOLD_KM = 0.1 // 100 meters
        private const val BEARING_SMOOTHING_FACTOR = 0.3f  // 0 = full smooth, 1 = raw GPS
        private const val SPEED_SMOOTHING_FACTOR = 0.4f
    }

    private var route: Route? = null
    private var smoothedBearing: Float = 0f
    private var hasBearing: Boolean = false
    private var smoothedSpeed: Float = 0f
    private var currentPointIndex: Int = 0
    private var currentManeuverIndex: Int = 0
    private var osmSpeedLimits: List<SpeedLimitSegment> = emptyList()

    fun setRoute(route: Route) {
        this.route = route
        this.currentPointIndex = 0
        this.currentManeuverIndex = 0
        this.osmSpeedLimits = emptyList()
        this.hasBearing = false
        this.smoothedSpeed = 0f
    }

    /** Set OSM-sourced speed limits fetched via Overpass. */
    fun setOsmSpeedLimits(segments: List<SpeedLimitSegment>) {
        this.osmSpeedLimits = segments
    }

    fun clear() {
        route = null
        currentPointIndex = 0
        currentManeuverIndex = 0
        osmSpeedLimits = emptyList()
        hasBearing = false
        smoothedSpeed = 0f
    }

    /**
     * Update navigation state with a new location fix.
     * Returns updated NavigationState or null if no route is set.
     */
    fun update(location: Location): NavigationState? {
        val activeRoute = route ?: return null
        if (activeRoute.points.size < 2) return null

        // Find closest point on route
        val closestResult = findClosestPointOnRoute(location, activeRoute.points)
        currentPointIndex = closestResult.segmentIndex

        // Check off-route
        val isOffRoute = closestResult.distanceKm > OFF_ROUTE_THRESHOLD_KM

        // Advance maneuver index based on position
        advanceManeuverIndex(activeRoute)

        val currentManeuver = activeRoute.maneuvers.getOrNull(currentManeuverIndex)
        val nextManeuver = activeRoute.maneuvers.getOrNull(currentManeuverIndex + 1)

        // Distance to next maneuver
        val distanceToNextManeuverKm = if (nextManeuver != null) {
            distanceFromLocationToManeuver(location, nextManeuver, activeRoute)
        } else if (currentManeuver != null) {
            // Last maneuver (destination) - distance to it
            haversineKm(
                location.latitude, location.longitude,
                currentManeuver.latitude, currentManeuver.longitude,
            )
        } else {
            0.0
        }

        // Current speed limit
        val currentSpeedLimit = findCurrentSpeedLimit(activeRoute)

        // Distance remaining
        val distanceRemainingKm = calculateDistanceRemaining(location, activeRoute)

        // ETA: segment-aware calculation using maneuver distance fractions.
        // Maneuvers have no per-segment duration from Valhalla, so we estimate each
        // maneuver's duration as its fraction of total route distance × total duration.
        // This is more accurate than pure proportional scaling because it sums only the
        // remaining maneuvers' estimated times, correctly excluding completed segments.
        val etaSeconds = calculateSegmentAwareEta(activeRoute, distanceRemainingKm)
        val rawSpeedKmh = (location.speed * 3.6).toFloat()
        val speedKmh = smoothSpeed(rawSpeedKmh)

        return NavigationState(
            route = activeRoute,
            isNavigating = true,
            currentManeuver = currentManeuver,
            nextManeuver = nextManeuver,
            distanceToNextManeuverKm = distanceToNextManeuverKm,
            currentSpeedKmh = speedKmh.toInt(),
            currentSpeedLimitKmh = currentSpeedLimit?.speedKmh,
            distanceRemainingKm = distanceRemainingKm,
            etaSeconds = etaSeconds,
            isOffRoute = isOffRoute,
            currentBearing = smoothBearing(location.bearing),
            currentLatitude = location.latitude,
            currentLongitude = location.longitude,
            currentManeuverIndex = currentManeuverIndex,
            currentPointIndex = currentPointIndex,
        )
    }

    fun updateTrafficDuration(newDurationWithTrafficSeconds: Long) {
        val currentRoute = route ?: return
        route = currentRoute.copy(durationWithTrafficSeconds = newDurationWithTrafficSeconds)
    }

    private fun smoothBearing(newBearing: Float): Float {
        if (!hasBearing) {
            hasBearing = true
            smoothedBearing = newBearing
            return newBearing
        }
        // Handle wrap-around (e.g., 350° -> 10°)
        var delta = newBearing - smoothedBearing
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        smoothedBearing += delta * BEARING_SMOOTHING_FACTOR
        // Normalize to 0-360
        smoothedBearing = ((smoothedBearing % 360) + 360) % 360
        return smoothedBearing
    }

    private fun smoothSpeed(rawSpeedKmh: Float): Float {
        smoothedSpeed += (rawSpeedKmh - smoothedSpeed) * SPEED_SMOOTHING_FACTOR
        return smoothedSpeed
    }

    private fun advanceManeuverIndex(route: Route) {
        // Advance if we've passed the current maneuver's position
        while (currentManeuverIndex < route.maneuvers.size - 1) {
            val nextManeuver = route.maneuvers[currentManeuverIndex + 1]
            // Find the point index closest to this maneuver
            val maneuverPointIdx = findClosestPointIndex(
                nextManeuver.latitude, nextManeuver.longitude, route.points,
            )
            if (currentPointIndex >= maneuverPointIdx) {
                currentManeuverIndex++
            } else {
                break
            }
        }
    }

    private fun findCurrentSpeedLimit(route: Route): SpeedLimit? {
        // First try Valhalla's explicit speed limits
        val valhallaLimit = route.speedLimits.firstOrNull { limit ->
            currentPointIndex in limit.startIndex..limit.endIndex
        }
        if (valhallaLimit != null) return valhallaLimit

        // Fall back to OSM maxspeed data from Overpass
        if (osmSpeedLimits.isEmpty()) return null
        val currentPoint = route.points.getOrNull(currentPointIndex) ?: return null

        // Find the nearest OSM speed limit segment within 500m
        val nearest = osmSpeedLimits.minByOrNull {
            haversineKm(currentPoint.latitude, currentPoint.longitude, it.latitude, it.longitude)
        } ?: return null

        val distKm = haversineKm(
            currentPoint.latitude, currentPoint.longitude,
            nearest.latitude, nearest.longitude,
        )
        // Only use if within 500m — beyond that, the road might be different
        return if (distKm < 0.5) {
            SpeedLimit(
                speedKmh = nearest.speedKmh,
                startIndex = currentPointIndex,
                endIndex = currentPointIndex,
            )
        } else {
            null
        }
    }

    private fun calculateDistanceRemaining(location: Location, route: Route): Double {
        if (route.points.isEmpty()) return 0.0
        val startIdx = min(currentPointIndex, route.points.size - 1)

        // Distance from current location to next route point
        var totalKm = if (startIdx < route.points.size) {
            haversineKm(
                location.latitude, location.longitude,
                route.points[startIdx].latitude, route.points[startIdx].longitude,
            )
        } else {
            0.0
        }

        // Sum segment distances from current point to end
        for (i in startIdx until route.points.size - 1) {
            totalKm += haversineKm(
                route.points[i].latitude, route.points[i].longitude,
                route.points[i + 1].latitude, route.points[i + 1].longitude,
            )
        }
        return totalKm
    }

    private fun distanceFromLocationToManeuver(
        location: Location,
        maneuver: Maneuver,
        route: Route,
    ): Double {
        // Direct distance along route segments from current position to maneuver
        val maneuverPointIdx = findClosestPointIndex(
            maneuver.latitude, maneuver.longitude, route.points,
        )
        if (maneuverPointIdx <= currentPointIndex) return 0.0

        var totalKm = haversineKm(
            location.latitude, location.longitude,
            route.points[currentPointIndex].latitude,
            route.points[currentPointIndex].longitude,
        )
        for (i in currentPointIndex until min(maneuverPointIdx, route.points.size - 1)) {
            totalKm += haversineKm(
                route.points[i].latitude, route.points[i].longitude,
                route.points[i + 1].latitude, route.points[i + 1].longitude,
            )
        }
        return totalKm
    }

    private data class ClosestPointResult(
        val segmentIndex: Int,
        val distanceKm: Double,
    )

    private fun findClosestPointOnRoute(
        location: Location,
        points: List<LatLng>,
    ): ClosestPointResult {
        var minDist = Double.MAX_VALUE
        var bestIdx = 0

        // Start search from near current position to avoid jumping backwards
        val searchStart = maxOf(0, currentPointIndex - 5)
        val searchEnd = min(points.size, currentPointIndex + 100)

        for (i in searchStart until searchEnd) {
            val dist = haversineKm(
                location.latitude, location.longitude,
                points[i].latitude, points[i].longitude,
            )
            if (dist < minDist) {
                minDist = dist
                bestIdx = i
            }
        }
        return ClosestPointResult(bestIdx, minDist)
    }

    private fun findClosestPointIndex(lat: Double, lon: Double, points: List<LatLng>): Int {
        var minDist = Double.MAX_VALUE
        var bestIdx = 0
        for (i in points.indices) {
            val dist = haversineKm(lat, lon, points[i].latitude, points[i].longitude)
            if (dist < minDist) {
                minDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    /**
     * Computes ETA by summing estimated durations for the remaining maneuvers.
     *
     * Because Valhalla does not expose per-maneuver time in the data we store, each
     * maneuver's duration is estimated as:
     *   maneuverDuration = (maneuver.distanceKm / route.distanceKm) * totalDuration
     *
     * For the maneuver we are currently inside, only the remaining fraction of that
     * maneuver's estimated duration is counted.
     *
     * This is more accurate than simple proportional scaling (distanceRemaining /
     * totalDistance * totalDuration) because it correctly attributes time to the
     * portions of the route that still lie ahead rather than assuming a uniform
     * time-per-km across the whole trip.
     */
    private fun calculateSegmentAwareEta(route: Route, distanceRemainingKm: Double): Long {
        val totalDuration = route.durationWithTrafficSeconds ?: route.durationSeconds
        val totalDistance = route.distanceKm

        if (totalDistance <= 0) return 0L

        val maneuvers = route.maneuvers
        if (maneuvers.isEmpty()) {
            // Fallback: plain proportional scaling
            return ((distanceRemainingKm / totalDistance) * totalDuration).toLong()
        }

        // distanceTravelled is how far along the route we are
        val distanceTravelled = (totalDistance - distanceRemainingKm).coerceAtLeast(0.0)

        var cumulativeDistance = 0.0
        var remainingEta = 0L
        var foundCurrent = false

        for (maneuver in maneuvers) {
            val maneuverDuration = (maneuver.distanceKm / totalDistance) * totalDuration
            val maneuverStart = cumulativeDistance
            cumulativeDistance += maneuver.distanceKm

            if (!foundCurrent) {
                if (cumulativeDistance > distanceTravelled) {
                    // We are somewhere inside this maneuver
                    foundCurrent = true
                    val distanceTravelledInManeuver = distanceTravelled - maneuverStart
                    val fractionRemaining = if (maneuver.distanceKm > 0) {
                        1.0 - (distanceTravelledInManeuver / maneuver.distanceKm)
                    } else {
                        0.0
                    }
                    remainingEta += (maneuverDuration * fractionRemaining).toLong()
                }
                // else: maneuver is already behind us — skip it
            } else {
                // This maneuver is entirely ahead of us
                remainingEta += maneuverDuration.toLong()
            }
        }

        return remainingEta
    }

    private fun haversineKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}
