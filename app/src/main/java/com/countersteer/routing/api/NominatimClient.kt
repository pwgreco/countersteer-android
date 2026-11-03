package com.countersteer.routing.api

import android.util.Log
import com.countersteer.routing.model.LatLng
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class NominatimResult(
    @SerialName("display_name") val displayName: String = "",
    val lat: String = "",
    val lon: String = "",
    @SerialName("place_id") val placeId: Long = 0,
    val type: String = "",
)

data class GeocodingResult(
    val displayName: String,
    val latLng: LatLng,
)

@Singleton
class NominatimClient @Inject constructor(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val TAG = "NominatimClient"
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val USER_AGENT = "Countersteer/1.0"
        private const val RATE_LIMIT_MS = 1100L // slightly over 1s to be safe
    }

    private val mutex = Mutex()
    private var lastRequestTime = 0L

    /** Returned when search fails due to network issues. */
    var lastSearchFailed: Boolean = false
        private set

    /**
     * Search for places. If [nearLocation] is provided, results are biased
     * toward that location (sorted by proximity).
     */
    suspend fun search(
        query: String,
        limit: Int = 5,
        nearLocation: LatLng? = null,
    ): List<GeocodingResult> {
        if (query.isBlank()) return emptyList()

        return try {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val elapsed = now - lastRequestTime
                if (elapsed < RATE_LIMIT_MS) {
                    kotlinx.coroutines.delay(RATE_LIMIT_MS - elapsed)
                }
                lastRequestTime = System.currentTimeMillis()
            }

            val results: List<NominatimResult> = httpClient.get(BASE_URL) {
                parameter("q", query)
                parameter("format", "json")
                parameter("limit", limit)
                header("User-Agent", USER_AGENT)

                // Bias results toward user's current location
                if (nearLocation != null) {
                    // viewbox: lon1,lat1,lon2,lat2 — a ~100km box around the user
                    val delta = 0.9 // ~100km in degrees
                    parameter(
                        "viewbox",
                        "${nearLocation.longitude - delta},${nearLocation.latitude + delta}," +
                            "${nearLocation.longitude + delta},${nearLocation.latitude - delta}",
                    )
                    // bounded=0 means prefer results in viewbox but don't exclude others
                    parameter("bounded", "0")
                }
            }.body()

            lastSearchFailed = false
            val mapped = results.mapNotNull { result ->
                val lat = result.lat.toDoubleOrNull() ?: return@mapNotNull null
                val lon = result.lon.toDoubleOrNull() ?: return@mapNotNull null
                GeocodingResult(
                    displayName = result.displayName,
                    latLng = LatLng(lat, lon),
                )
            }

            // Sort by distance to user (closest first)
            if (nearLocation != null) {
                mapped.sortedBy { haversineKm(nearLocation, it.latLng) }
            } else {
                mapped
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding search failed for: $query", e)
            lastSearchFailed = true
            emptyList()
        }
    }

    /** Haversine distance in kilometres between two points. */
    private fun haversineKm(a: LatLng, b: LatLng): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
