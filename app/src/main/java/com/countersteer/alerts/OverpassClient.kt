package com.countersteer.alerts

import android.util.Log
import com.countersteer.data.dao.MarkerDao
import com.countersteer.data.entity.MarkerSource
import com.countersteer.data.entity.MarkerType
import com.countersteer.data.entity.RoadMarkerEntity
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** A fuel station found from OSM Overpass data. */
data class FuelStation(
    val latitude: Double,
    val longitude: Double,
    val name: String?,
)

/** A road segment with a posted speed limit from OSM. */
data class SpeedLimitSegment(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Int,
)

/**
 * Queries the OSM Overpass API for enforcement nodes (speed cameras, etc.)
 * near a route and caches results in Room. Also queries maxspeed tags
 * for speed limit display during navigation.
 */
@Singleton
class OverpassClient @Inject constructor(
    private val httpClient: HttpClient,
    private val markerDao: MarkerDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Track queried areas to avoid redundant requests.
    // Key: "lat_lon" rounded to 2 decimal places (~1 km grid)
    private val queriedCells = mutableSetOf<String>()

    companion object {
        private const val TAG = "OverpassClient"
        private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
        private const val SEARCH_RADIUS_M = 5000 // metres around each sample point
        private const val SAMPLE_INTERVAL_KM = 4.0 // sample route every ~4 km
    }

    /**
     * Query enforcement data along the given route points.
     * Samples every [SAMPLE_INTERVAL_KM] km and queries Overpass around each sample.
     * Results are saved to Room with source = OSM.
     */
    suspend fun fetchEnforcementAlongRoute(
        routePoints: List<com.countersteer.routing.model.LatLng>,
    ) {
        if (routePoints.isEmpty()) return

        val samplePoints = sampleRoutePoints(routePoints)
        for (point in samplePoints) {
            val cellKey = cellKey(point.latitude, point.longitude)
            if (cellKey in queriedCells) continue

            try {
                val markers = queryOverpass(point.latitude, point.longitude)
                for (marker in markers) {
                    // Check if we already have an OSM marker near this location
                    val existing = markerDao.getNearby(
                        marker.latitude,
                        marker.longitude,
                        50.0, // 50m dedup radius
                    ).first()
                    val alreadyExists = existing.any { it.source == MarkerSource.OSM }
                    if (!alreadyExists) {
                        markerDao.insert(marker)
                    }
                }
                queriedCells.add(cellKey)
            } catch (e: Exception) {
                Log.w(TAG, "Overpass query failed for $cellKey", e)
            }
        }
    }

    private fun sampleRoutePoints(
        points: List<com.countersteer.routing.model.LatLng>,
    ): List<com.countersteer.routing.model.LatLng> {
        if (points.isEmpty()) return emptyList()
        val samples = mutableListOf(points.first())
        var accumulatedKm = 0.0

        for (i in 1 until points.size) {
            val dist = haversineKm(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
            accumulatedKm += dist
            if (accumulatedKm >= SAMPLE_INTERVAL_KM) {
                samples.add(points[i])
                accumulatedKm = 0.0
            }
        }
        // Always include the last point
        if (samples.last() != points.last()) {
            samples.add(points.last())
        }
        return samples
    }

    private suspend fun queryOverpass(lat: Double, lon: Double): List<RoadMarkerEntity> {
        val query = """
            [out:json][timeout:25];
            (
                node["enforcement"](around:$SEARCH_RADIUS_M,$lat,$lon);
                node["highway"="speed_camera"](around:$SEARCH_RADIUS_M,$lat,$lon);
                node["highway"="red_light_camera"](around:$SEARCH_RADIUS_M,$lat,$lon);
            );
            out body;
        """.trimIndent()

        val response = httpClient.get(OVERPASS_URL) {
            parameter("data", query)
        }
        val body = response.bodyAsText()
        return parseOverpassResponse(body)
    }

    private fun parseOverpassResponse(responseBody: String): List<RoadMarkerEntity> {
        val markers = mutableListOf<RoadMarkerEntity>()
        try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val elements = root["elements"]?.jsonArray ?: return emptyList()

            for (element in elements) {
                val obj = element.jsonObject
                val lat = obj["lat"]?.jsonPrimitive?.double ?: continue
                val lon = obj["lon"]?.jsonPrimitive?.double ?: continue
                val tags = obj["tags"]?.jsonObject

                val enforcementType = tags?.get("enforcement")?.jsonPrimitive?.contentOrNull
                val highwayType = tags?.get("highway")?.jsonPrimitive?.contentOrNull
                val markerType = mapEnforcementType(enforcementType, highwayType)
                val note = buildNote(tags)

                markers.add(
                    RoadMarkerEntity(
                        latitude = lat,
                        longitude = lon,
                        type = markerType,
                        note = note,
                        source = MarkerSource.OSM,
                        createdAt = System.currentTimeMillis(),
                        alertRadiusM = 1000,
                    ),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Overpass response", e)
        }
        return markers
    }

    private fun mapEnforcementType(enforcementType: String?, highwayType: String?): MarkerType {
        // Check highway tag first (more specific)
        when (highwayType) {
            "speed_camera" -> return MarkerType.SPEED_CAMERA
            "red_light_camera" -> return MarkerType.RED_LIGHT_CAMERA
        }
        // Fall back to enforcement tag
        return when (enforcementType) {
            "speed_camera", "maxspeed", "average_speed" -> MarkerType.SPEED_CAMERA
            "red_light_camera", "traffic_signals" -> MarkerType.RED_LIGHT_CAMERA
            else -> MarkerType.SPEED_CAMERA // Default enforcement to speed camera
        }
    }

    private fun buildNote(tags: JsonObject?): String? {
        if (tags == null) return null
        val parts = mutableListOf<String>()
        tags["enforcement"]?.jsonPrimitive?.contentOrNull?.let { parts.add(it.replace('_', ' ')) }
        tags["maxspeed"]?.jsonPrimitive?.contentOrNull?.let { parts.add("limit: $it") }
        tags["name"]?.jsonPrimitive?.contentOrNull?.let { parts.add(it) }
        return parts.joinToString(", ").ifBlank { null }
    }

    /**
     * Fetch posted speed limits (maxspeed tags) from OSM along a route.
     * Returns a map of (lat, lon) → speed limit in km/h.
     * Queries ways (roads) near sampled route points.
     */
    suspend fun fetchSpeedLimitsAlongRoute(
        routePoints: List<com.countersteer.routing.model.LatLng>,
    ): List<SpeedLimitSegment> {
        if (routePoints.isEmpty()) return emptyList()

        val samplePoints = sampleRoutePoints(routePoints)
        val allSegments = mutableListOf<SpeedLimitSegment>()

        for (point in samplePoints) {
            try {
                val segments = queryMaxspeed(point.latitude, point.longitude)
                allSegments.addAll(segments)
            } catch (e: Exception) {
                Log.w(TAG, "Maxspeed query failed for ${point.latitude},${point.longitude}", e)
            }
        }

        return allSegments
    }

    /**
     * Query fuel stations near the given route points using a bounding-box query.
     * Returns a list of [FuelStation] objects found within ~2 km of the route.
     */
    suspend fun getFuelStationsNearRoute(
        points: List<com.countersteer.routing.model.LatLng>,
    ): List<FuelStation> {
        if (points.isEmpty()) return emptyList()

        // Build bounding box from route points with a ~2km buffer
        val lats = points.map { it.latitude }
        val lons = points.map { it.longitude }
        val buffer = 0.02 // ~2km
        val bbox = "${lats.min() - buffer},${lons.min() - buffer},${lats.max() + buffer},${lons.max() + buffer}"

        val query = """
            [out:json][timeout:10];
            (
                node["amenity"="fuel"]($bbox);
                way["amenity"="fuel"]($bbox);
            );
            out center;
        """.trimIndent()

        // Retry up to 3 times with backoff — Overpass rate-limits concurrent queries
        for (attempt in 1..3) {
            val response = httpClient.get(OVERPASS_URL) {
                parameter("data", query)
            }
            val body = response.bodyAsText().trim()
            Log.d(TAG, "Fuel station query attempt=$attempt bbox=$bbox, response length=${body.length}")

            // Overpass returns XML error pages when rate-limited
            if (body.startsWith("<") || !body.startsWith("{")) {
                Log.w(TAG, "Overpass returned non-JSON (rate limited?), attempt $attempt: ${body.take(100)}")
                if (attempt < 3) {
                    kotlinx.coroutines.delay(attempt * 2000L) // 2s, 4s backoff
                    continue
                }
                return emptyList()
            }
            return parseFuelStationResponse(body)
        }
        return emptyList()
    }

    private fun parseFuelStationResponse(responseBody: String): List<FuelStation> {
        val stations = mutableListOf<FuelStation>()
        try {
            // Extract just the JSON object in case of trailing content
            val jsonStart = responseBody.indexOf('{')
            val jsonEnd = responseBody.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd < 0) {
                Log.w(TAG, "No JSON object in fuel station response: ${responseBody.take(200)}")
                return emptyList()
            }
            val cleanJson = responseBody.substring(jsonStart, jsonEnd + 1)
            val root = json.parseToJsonElement(cleanJson).jsonObject
            val elements = root["elements"]?.jsonArray ?: return emptyList()

            for (element in elements) {
                val obj = element.jsonObject
                // Nodes have top-level lat/lon; ways have center.lat/center.lon
                val lat = obj["lat"]?.jsonPrimitive?.double
                    ?: obj["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.double
                    ?: continue
                val lon = obj["lon"]?.jsonPrimitive?.double
                    ?: obj["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.double
                    ?: continue
                val tags = obj["tags"]?.jsonObject
                val name = tags?.get("name")?.jsonPrimitive?.contentOrNull

                stations.add(FuelStation(lat, lon, name))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse fuel station response", e)
        }
        return stations
    }

    private suspend fun queryMaxspeed(lat: Double, lon: Double): List<SpeedLimitSegment> {
        val query = """
            [out:json][timeout:10];
            way["maxspeed"](around:200,$lat,$lon);
            out tags center;
        """.trimIndent()

        val response = httpClient.get(OVERPASS_URL) {
            parameter("data", query)
        }
        val body = response.bodyAsText()
        return parseMaxspeedResponse(body)
    }

    private fun parseMaxspeedResponse(responseBody: String): List<SpeedLimitSegment> {
        val segments = mutableListOf<SpeedLimitSegment>()
        try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val elements = root["elements"]?.jsonArray ?: return emptyList()

            for (element in elements) {
                val obj = element.jsonObject
                val tags = obj["tags"]?.jsonObject ?: continue
                val maxspeedStr = tags["maxspeed"]?.jsonPrimitive?.contentOrNull ?: continue

                // Parse maxspeed value — could be "50", "50 km/h", "30 mph"
                val speedKmh = parseMaxspeedValue(maxspeedStr) ?: continue

                // Use the way's center point
                val center = obj["center"]?.jsonObject
                val lat = center?.get("lat")?.jsonPrimitive?.double ?: continue
                val lon = center?.get("lon")?.jsonPrimitive?.double ?: continue

                segments.add(SpeedLimitSegment(lat, lon, speedKmh))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse maxspeed response", e)
        }
        return segments
    }

    /**
     * Parse OSM maxspeed value to km/h.
     * Handles: "50", "50 km/h", "30 mph", "none", "walk"
     */
    private fun parseMaxspeedValue(value: String): Int? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none" || trimmed == "signals" || trimmed == "walk") return null

        // Check for mph
        if (trimmed.contains("mph")) {
            val num = trimmed.replace(Regex("[^0-9]"), "").toIntOrNull() ?: return null
            return (num * 1.60934).toInt()
        }

        // Default: km/h (strip non-numeric chars)
        return trimmed.replace(Regex("[^0-9]"), "").toIntOrNull()
    }

    fun clearCache() {
        queriedCells.clear()
    }

    private fun cellKey(lat: Double, lon: Double): String {
        val latRounded = "%.2f".format(lat)
        val lonRounded = "%.2f".format(lon)
        return "${latRounded}_${lonRounded}"
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
}
