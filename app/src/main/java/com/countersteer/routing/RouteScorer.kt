package com.countersteer.routing

import com.countersteer.routing.model.LatLng
import com.countersteer.routing.model.Maneuver
import com.countersteer.routing.model.Route
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

@Singleton
class RouteScorer @Inject constructor() {

    /**
     * Calculate curvature score (0-100) from route geometry.
     * Higher score = more curves = more fun for motorcycling.
     */
    fun calculateCurvatureScore(points: List<LatLng>): Double {
        if (points.size < 3) return 0.0

        var totalAngleChange = 0.0
        var segmentCount = 0

        for (i in 1 until points.size - 1) {
            val bearing1 = bearing(points[i - 1], points[i])
            val bearing2 = bearing(points[i], points[i + 1])
            var angleDiff = abs(bearing2 - bearing1)
            if (angleDiff > 180.0) angleDiff = 360.0 - angleDiff

            // Only count meaningful turns (> 5 degrees)
            if (angleDiff > 5.0) {
                totalAngleChange += angleDiff
                segmentCount++
            }
        }

        if (segmentCount == 0) return 0.0

        // Average angle change per turn, normalized.
        // A typical curvy road might average 20-40 degrees per turn.
        val avgAngle = totalAngleChange / segmentCount
        // Frequency of turns relative to total points
        val turnFrequency = segmentCount.toDouble() / (points.size - 2)

        // Combine average angle magnitude and turn frequency
        val angleScore = min(avgAngle / 45.0, 1.0) * 60.0
        val frequencyScore = min(turnFrequency / 0.3, 1.0) * 40.0

        return (angleScore + frequencyScore).coerceIn(0.0, 100.0)
    }

    /**
     * Calculate scenic score (0-100) based on road types from maneuvers.
     * Prefers secondary/tertiary roads, penalizes motorways.
     */
    fun calculateScenicScore(maneuvers: List<Maneuver>, distanceKm: Double): Double {
        if (maneuvers.isEmpty() || distanceKm <= 0) return 50.0

        // Score based on maneuver types — more diverse routing with many named
        // streets on smaller roads is typically more scenic
        var namedRoads = 0
        var totalManeuvers = maneuvers.size

        for (m in maneuvers) {
            if (m.streetName.isNotBlank()) {
                namedRoads++
            }
        }

        // More named roads = more scenic (riding through towns/countryside)
        val namedRatio = if (totalManeuvers > 0) {
            namedRoads.toDouble() / totalManeuvers
        } else 0.5

        // More maneuvers per km = smaller roads = more scenic
        val maneuverDensity = totalManeuvers.toDouble() / distanceKm
        val densityScore = min(maneuverDensity / 0.5, 1.0) * 40.0
        val namedScore = namedRatio * 60.0

        return (densityScore + namedScore).coerceIn(0.0, 100.0)
    }

    /**
     * Apply scores to a route, returning a new Route with scores filled in.
     */
    fun scoreRoute(route: Route): Route {
        val curvature = calculateCurvatureScore(route.points)
        val scenic = calculateScenicScore(route.maneuvers, route.distanceKm)
        return route.copy(
            curvatureScore = curvature,
            scenicScore = scenic,
        )
    }

    private fun bearing(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
            kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)

        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}
