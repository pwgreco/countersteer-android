package com.countersteer.routing.api

import android.util.Log
import com.countersteer.data.entity.RouteType
import com.countersteer.routing.model.LatLng
import com.countersteer.routing.model.Maneuver
import com.countersteer.routing.model.Route
import com.countersteer.routing.model.SpeedLimit
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class ValhallaClient @Inject constructor(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val TAG = "ValhallaClient"
        private const val BASE_URL = "https://valhalla1.openstreetmap.de/route"
        private const val POLYLINE_PRECISION = 6

        fun decodePolyline(encoded: String): List<LatLng> {
            val factor = 10.0.pow(POLYLINE_PRECISION)
            val result = mutableListOf<LatLng>()
            var index = 0
            var lat = 0
            var lng = 0

            while (index < encoded.length) {
                var shift = 0
                var b: Int
                var dlat = 0
                do {
                    b = encoded[index++].code - 63
                    dlat = dlat or ((b and 0x1F) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lat += if (dlat and 1 != 0) (dlat shr 1).inv() else dlat shr 1

                shift = 0
                var dlng = 0
                do {
                    b = encoded[index++].code - 63
                    dlng = dlng or ((b and 0x1F) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lng += if (dlng and 1 != 0) (dlng shr 1).inv() else dlng shr 1

                result.add(LatLng(lat / factor, lng / factor))
            }
            return result
        }
    }

    /**
     * Request a single route with alternates. Returns up to 3 routes.
     */
    suspend fun requestRouteWithAlternates(
        waypoints: List<LatLng>,
        costingOptions: ValhallaMotorcycleOptions? = null,
    ): List<Route> {
        return try {
            val locations = waypoints.map { ValhallaLocation(lat = it.latitude, lon = it.longitude) }

            val request = ValhallaRouteRequest(
                locations = locations,
                costingOptions = costingOptions,
                alternates = 2,
            )

            Log.d(TAG, "Requesting route with alternates, ${locations.size} waypoints")

            var httpResponse = httpClient.post(BASE_URL) {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Countersteer/1.0")
                setBody(request)
            }

            // Retry once on 429 rate limit
            if (httpResponse.status.value == 429) {
                Log.w(TAG, "Rate limited by Valhalla, retrying in 2s")
                delay(2000)
                httpResponse = httpClient.post(BASE_URL) {
                    contentType(ContentType.Application.Json)
                    header("User-Agent", "Countersteer/1.0")
                    setBody(request)
                }
            }

            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                Log.e(TAG, "Valhalla API error ${httpResponse.status}: $errorBody")
                throw RouteGenerationException("Route server error: ${httpResponse.status.value}")
            }

            val response: ValhallaRouteResponse = httpResponse.body()
            Log.d(TAG, "Valhalla response: trip=${response.trip != null}, alternates=${response.alternates?.size ?: 0}")

            val routes = mutableListOf<Route>()

            // Parse main route
            parseTrip(response.trip, RouteType.QUICKEST)?.let { routes.add(it) }

            // Parse alternates
            response.alternates?.forEachIndexed { index, alt ->
                parseTrip(alt.trip, RouteType.QUICKEST)?.let { routes.add(it) }
            }

            Log.d(TAG, "Parsed ${routes.size} routes from response")
            routes
        } catch (e: CancellationException) {
            throw e
        } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
            Log.e(TAG, "Route request timed out", e)
            throw RouteGenerationException("Route server is not responding. Check your internet connection and try again.")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network error for route request", e)
            throw RouteGenerationException("Unable to connect to the route server. Check your internet connection.")
        } catch (e: RouteGenerationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Route request failed", e)
            emptyList()
        }
    }

    /**
     * Request a single route with specific costing (no alternates).
     */
    suspend fun requestRoute(
        waypoints: List<LatLng>,
        routeType: RouteType,
        costingOptions: ValhallaMotorcycleOptions? = null,
    ): Route? {
        return try {
            val locations = waypoints.map { ValhallaLocation(lat = it.latitude, lon = it.longitude) }

            val request = ValhallaRouteRequest(
                locations = locations,
                costingOptions = costingOptions,
            )

            Log.d(TAG, "Requesting $routeType route with ${locations.size} waypoints")

            var httpResponse = httpClient.post(BASE_URL) {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Countersteer/1.0")
                setBody(request)
            }

            // Retry once on 429 rate limit
            if (httpResponse.status.value == 429) {
                Log.w(TAG, "Rate limited by Valhalla, retrying in 2s")
                delay(2000)
                httpResponse = httpClient.post(BASE_URL) {
                    contentType(ContentType.Application.Json)
                    header("User-Agent", "Countersteer/1.0")
                    setBody(request)
                }
            }

            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                Log.e(TAG, "Valhalla API error ${httpResponse.status}: $errorBody")
                return null
            }

            val response: ValhallaRouteResponse = httpResponse.body()
            parseTrip(response.trip, routeType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Route request failed for $routeType", e)
            null
        }
    }

    private fun parseTrip(trip: ValhallaTrip?, routeType: RouteType): Route? {
        if (trip == null) {
            Log.e(TAG, "Valhalla response has no trip data")
            return null
        }
        if (trip.status != null && trip.status != 0) {
            Log.e(TAG, "Valhalla trip error: status=${trip.status}, message=${trip.statusMessage}")
            return null
        }
        if (trip.legs.isEmpty()) {
            Log.e(TAG, "Valhalla trip has no legs")
            return null
        }

        // Combine shapes from all legs for multi-waypoint routes
        val allShapes = trip.legs.mapNotNull { it.shape }
        if (allShapes.isEmpty()) return null

        val allPoints = mutableListOf<LatLng>()
        allShapes.forEachIndexed { index, shape ->
            val decoded = decodePolyline(shape)
            if (index > 0 && allPoints.isNotEmpty() && decoded.isNotEmpty()) {
                // Skip first point of subsequent legs (overlaps with last point of previous)
                allPoints.addAll(decoded.drop(1))
            } else {
                allPoints.addAll(decoded)
            }
        }
        if (allPoints.isEmpty()) return null

        // Use first leg's shape as the encoded polyline for storage
        val encodedPolyline = allShapes.first()

        var shapeOffset = 0
        val allManeuvers = trip.legs.flatMap { leg ->
            val legManeuvers = leg.maneuvers.map { m ->
                Maneuver(
                    instruction = m.instruction,
                    type = m.type,
                    streetName = m.streetNames.firstOrNull() ?: "",
                    distanceKm = m.length,
                    latitude = allPoints.getOrNull(m.beginShapeIndex + shapeOffset)?.latitude ?: 0.0,
                    longitude = allPoints.getOrNull(m.beginShapeIndex + shapeOffset)?.longitude ?: 0.0,
                )
            }
            shapeOffset += (leg.maneuvers.lastOrNull()?.endShapeIndex ?: 0)
            legManeuvers
        }

        // Sum up distance and time from all legs
        val totalDistanceKm = trip.summary?.length
            ?: trip.legs.sumOf { it.summary?.length ?: 0.0 }
        val totalDurationSeconds = trip.summary?.time?.toLong()
            ?: trip.legs.sumOf { it.summary?.time?.toLong() ?: 0L }

        val hasUnpaved = trip.legs.any { leg ->
            leg.maneuvers.any { it.roughSurface == true }
        }

        // Speed limits from Valhalla (when available — often empty)
        // Real speed limits are fetched from OSM via Overpass at navigation start
        val speedLimits = trip.legs.flatMap { leg ->
            leg.maneuvers.mapNotNull { m ->
                m.speedLimit?.let { limit ->
                    SpeedLimit(
                        speedKmh = limit,
                        startIndex = m.beginShapeIndex,
                        endIndex = m.endShapeIndex,
                    )
                }
            }
        }

        return Route(
            type = routeType,
            polyline = encodedPolyline,
            points = allPoints,
            distanceKm = totalDistanceKm,
            durationSeconds = totalDurationSeconds,
            durationWithTrafficSeconds = null,
            curvatureScore = 0.0,
            scenicScore = 0.0,
            maneuvers = allManeuvers,
            speedLimits = speedLimits,
            hasUnpavedSegments = hasUnpaved,
        )
    }


}

/** User-facing exception for route generation failures. */
class RouteGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
