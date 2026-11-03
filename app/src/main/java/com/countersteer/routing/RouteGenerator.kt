package com.countersteer.routing

import android.util.Log
import com.countersteer.data.entity.RouteType
import com.countersteer.routing.api.MotorcycleCostingOptions
import com.countersteer.routing.api.TomTomTrafficClient
import com.countersteer.routing.api.ValhallaClient
import com.countersteer.routing.api.ValhallaMotorcycleOptions
import com.countersteer.routing.model.LatLng
import com.countersteer.routing.model.Route
import com.countersteer.routing.model.RouteShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class RouteGenerator @Inject constructor(
    private val valhallaClient: ValhallaClient,
    private val tomTomTrafficClient: TomTomTrafficClient,
    private val routeScorer: RouteScorer,
    private val routeFilter: RouteFilter,
) {
    companion object {
        private const val TAG = "RouteGenerator"
        // Roads are typically 1.4x longer than straight-line distance
        private const val ROAD_DISTANCE_FACTOR = 1.4
    }

    /**
     * Generate route variants for destination mode.
     * Uses two strategies: back-road costing with alternates + highway costing.
     * Scores all results and labels the curviest, most scenic, and fastest.
     */
    suspend fun generateDestinationRoutes(
        origin: LatLng,
        destination: LatLng,
    ): List<Route> {
        val waypoints = listOf(origin, destination)

        // Sequential requests to avoid 429 rate limiting from FOSSGIS Valhalla
        val backRoads = valhallaClient.requestRouteWithAlternates(
            waypoints,
            ValhallaMotorcycleOptions(
                motorcycle = MotorcycleCostingOptions(
                    useHighways = 0.1,
                    highwayFactor = 4.0,
                ),
            ),
        )

        delay(200) // Small delay between requests to reduce rate-limit pressure

        val highway = valhallaClient.requestRoute(
            waypoints,
            RouteType.QUICKEST,
            ValhallaMotorcycleOptions(
                motorcycle = MotorcycleCostingOptions(
                    useHighways = 1.0,
                ),
            ),
        )

        val allRoutes = mutableListOf<Route>()
        allRoutes.addAll(backRoads)
        highway?.let { allRoutes.add(it) }

        Log.d(TAG, "Got ${allRoutes.size} candidate routes for destination mode")
        return labelAndScoreRoutes(allRoutes)
    }

    /**
     * Generate route variants for explore mode (loop route).
     * Takes a compass bearing (0-360) and round-trip distance in km.
     */
    suspend fun generateExploreRoutes(
        origin: LatLng,
        bearingDegrees: Double,
        distanceKm: Double,
    ): List<Route> {
        val waypoints = generateLoopWaypoints(origin, bearingDegrees, distanceKm)
        Log.d(TAG, "Explore: bearing=$bearingDegrees, distance=$distanceKm, waypoints=${waypoints.size}")
        waypoints.forEach { Log.d(TAG, "  WP: ${it.latitude}, ${it.longitude}") }

        // Sequential requests to avoid 429 rate limiting from FOSSGIS Valhalla
        val backRoads = valhallaClient.requestRouteWithAlternates(
            waypoints,
            ValhallaMotorcycleOptions(
                motorcycle = MotorcycleCostingOptions(
                    useHighways = 0.1,
                    highwayFactor = 4.0,
                ),
            ),
        )

        delay(200)

        val highway = valhallaClient.requestRoute(
            waypoints,
            RouteType.QUICKEST,
            ValhallaMotorcycleOptions(
                motorcycle = MotorcycleCostingOptions(
                    useHighways = 1.0,
                ),
            ),
        )

        val allRoutes = mutableListOf<Route>()
        allRoutes.addAll(backRoads)
        highway?.let { allRoutes.add(it) }

        Log.d(TAG, "Got ${allRoutes.size} candidate routes for explore mode")
        return labelAndScoreRoutes(allRoutes)
    }

    /**
     * Generate route variants for explore mode with Out & Back shape.
     * Routes out to a destination point, then returns the same way.
     * Distance/duration are doubled to reflect the full round trip.
     */
    suspend fun generateOutAndBackRoutes(
        origin: LatLng,
        bearingDegrees: Double,
        distanceKm: Double,
    ): List<Route> {
        val waypoints = generateOutAndBackWaypoints(origin, bearingDegrees, distanceKm)
        Log.d(TAG, "Out&Back: bearing=$bearingDegrees, distance=$distanceKm, waypoints=${waypoints.size}")
        waypoints.forEach { Log.d(TAG, "  WP: ${it.latitude}, ${it.longitude}") }

        // Sequential requests to avoid 429 rate limiting from FOSSGIS Valhalla
        val backRoads = valhallaClient.requestRouteWithAlternates(
            waypoints,
            ValhallaMotorcycleOptions(
                motorcycle = MotorcycleCostingOptions(
                    useHighways = 0.1,
                    highwayFactor = 4.0,
                ),
            ),
        )

        delay(200)

        val highway = valhallaClient.requestRoute(
            waypoints,
            RouteType.QUICKEST,
            ValhallaMotorcycleOptions(
                motorcycle = MotorcycleCostingOptions(
                    useHighways = 1.0,
                ),
            ),
        )

        val allRoutes = mutableListOf<Route>()
        allRoutes.addAll(backRoads)
        highway?.let { allRoutes.add(it) }

        Log.d(TAG, "Got ${allRoutes.size} candidate routes for out & back mode")

        // Double distance and duration for the return trip
        val doubled = allRoutes.map { route ->
            route.copy(
                distanceKm = route.distanceKm * 2.0,
                durationSeconds = route.durationSeconds * 2,
                durationWithTrafficSeconds = route.durationWithTrafficSeconds?.let { it * 2 },
                isOutAndBack = true,
            )
        }

        return labelAndScoreRoutes(doubled)
    }

    /**
     * Generate waypoints for Out & Back mode.
     * Simply origin → destination point at half the total distance along the bearing.
     */
    internal fun generateOutAndBackWaypoints(
        origin: LatLng,
        bearingDegrees: Double,
        roundTripDistanceKm: Double,
    ): List<LatLng> {
        val oneWayDist = (roundTripDistanceKm / 2.0) / ROAD_DISTANCE_FACTOR
        val bearingRad = Math.toRadians(bearingDegrees)
        val destination = movePoint(origin, bearingRad, oneWayDist)
        return listOf(origin, destination)
    }

    /**
     * Score all candidate routes, then assign RouteType labels:
     * - CURVY = highest curvature score
     * - QUICKEST = shortest duration
     * - SCENIC = highest scenic score (or remaining route)
     *
     * Always returns up to 3 distinctly-labeled routes.
     */
    private suspend fun labelAndScoreRoutes(candidates: List<Route>): List<Route> {
        if (candidates.isEmpty()) return emptyList()

        // Score all candidates
        val scored = candidates.map { route ->
            val withScores = routeScorer.scoreRoute(route)
            val trafficFactor = try {
                tomTomTrafficClient.getTrafficFactor(route.points)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Traffic fetch failed, using base estimate", e)
                null
            }
            val adjustedDuration = tomTomTrafficClient.adjustDurationForTraffic(
                withScores.durationSeconds,
                trafficFactor,
                withScores.distanceKm,
            )
            val hasUnpaved = routeFilter.hasUnpavedSegments(withScores)

            withScores.copy(
                durationWithTrafficSeconds = adjustedDuration,
                hasUnpavedSegments = hasUnpaved,
            )
        }

        // If only 1 route, return it as CURVY
        if (scored.size == 1) {
            return listOf(scored.first().copy(type = RouteType.CURVY))
        }

        val result = mutableListOf<Route>()
        val used = mutableSetOf<Int>()

        // CURVY = highest curvature score
        val curviestIdx = scored.indices.maxByOrNull { scored[it].curvatureScore } ?: 0
        result.add(scored[curviestIdx].copy(type = RouteType.CURVY))
        used.add(curviestIdx)

        // QUICKEST = shortest effective duration (traffic-adjusted if available)
        val quickestIdx = scored.indices
            .filter { it !in used }
            .minByOrNull { scored[it].durationWithTrafficSeconds ?: scored[it].durationSeconds }
        if (quickestIdx != null) {
            result.add(scored[quickestIdx].copy(type = RouteType.QUICKEST))
            used.add(quickestIdx)
        }

        // SCENIC = highest scenic score among remaining, or just the remaining route
        val scenicIdx = scored.indices
            .filter { it !in used }
            .maxByOrNull { scored[it].scenicScore }
        if (scenicIdx != null) {
            result.add(scored[scenicIdx].copy(type = RouteType.SCENIC))
        }

        Log.d(TAG, "Labeled ${result.size} routes: ${result.map { "${it.type}: ${it.distanceKm}km, curv=${it.curvatureScore}, scenic=${it.scenicScore}" }}")
        return result
    }

    /**
     * Generate loop waypoints for explore mode.
     * Places waypoints strongly in the chosen compass direction to ensure
     * the route goes where the user pointed.
     */
    internal fun generateLoopWaypoints(
        origin: LatLng,
        bearingDegrees: Double,
        roundTripDistanceKm: Double,
    ): List<LatLng> {
        // Road distance correction: roads are ~1.4x longer than straight line
        val correctedDistance = roundTripDistanceKm / ROAD_DISTANCE_FACTOR
        val halfDist = correctedDistance / 2.0
        val bearingRad = Math.toRadians(bearingDegrees)

        // Lateral offset angle (perpendicular to bearing)
        val offsetAngleRight = bearingRad + Math.PI / 4  // 45° offset for wider loop
        val offsetAngleLeft = bearingRad - Math.PI / 4

        // Place the apex (farthest point) at ~40% of the corrected one-way distance
        // This ensures the route strongly commits to the chosen direction
        val apexDist = halfDist * 0.4
        val lateralOffset = halfDist * 0.12

        // Apex waypoint — directly along the bearing, the farthest point
        val apex = movePoint(origin, bearingRad, apexDist)

        // Outbound waypoint — offset right from the bearing line
        val outbound = movePoint(origin, offsetAngleRight, apexDist * 0.6)

        // Return waypoint — offset left from the bearing line
        val returnWp = movePoint(origin, offsetAngleLeft, apexDist * 0.6)

        return listOf(origin, outbound, apex, returnWp, origin)
    }

    /**
     * Move a point by distance (km) along a bearing (radians).
     */
    private fun movePoint(from: LatLng, bearingRad: Double, distanceKm: Double): LatLng {
        val latOffset = (distanceKm * cos(bearingRad)) / 111.0
        val lonOffset = (distanceKm * sin(bearingRad)) / (111.0 * cos(Math.toRadians(from.latitude)))
        return LatLng(
            latitude = from.latitude + latOffset,
            longitude = from.longitude + lonOffset,
        )
    }
}
