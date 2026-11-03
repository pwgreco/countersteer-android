package com.countersteer.routing.api

import android.util.Log
import com.countersteer.data.SecurePreferencesRepository
import com.countersteer.routing.model.LatLng
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TomTomTrafficClient @Inject constructor(
    private val httpClient: HttpClient,
    private val securePrefs: SecurePreferencesRepository,
) {
    companion object {
        private const val TAG = "TomTomTrafficClient"
        private const val BASE_URL =
            "https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json"
        private const val SAMPLE_POINTS = 12
    }

    /**
     * Fetch traffic flow data by sampling points along the route.
     * Returns a traffic factor (ratio of current speed to free-flow speed).
     * A value of 1.0 means normal traffic, <1.0 means slower than normal.
     * Returns null if no API key is configured or the request fails.
     */
    suspend fun getTrafficFactor(routePoints: List<LatLng>): Double? {
        val apiKey = securePrefs.getTomTomApiKey() ?: return null
        if (routePoints.isEmpty()) return null

        return try {
            val samplePoints = sampleRoutePoints(routePoints, SAMPLE_POINTS)
            val factors = samplePoints.mapNotNull { point ->
                queryTrafficFlow(point, apiKey)
            }
            if (factors.isEmpty()) return null

            val avgFactor = factors.sum() / factors.size
            avgFactor.coerceIn(0.5, 1.5)
        } catch (e: Exception) {
            Log.e(TAG, "Traffic flow request failed", e)
            null
        }
    }

    private suspend fun queryTrafficFlow(point: LatLng, apiKey: String): Double? {
        return try {
            val response: TomTomFlowResponse = httpClient.get(BASE_URL) {
                parameter("point", "${point.latitude},${point.longitude}")
                parameter("key", apiKey)
                parameter("unit", "KMPH")
            }.body()

            val data = response.flowSegmentData ?: return null
            val currentSpeed = data.currentSpeed ?: return null
            val freeFlowSpeed = data.freeFlowSpeed ?: return null
            if (freeFlowSpeed <= 0) return null

            currentSpeed / freeFlowSpeed
        } catch (e: Exception) {
            Log.w(TAG, "Traffic query failed for point $point", e)
            null
        }
    }

    /**
     * Sample evenly-spaced points along the route for traffic queries.
     */
    private fun sampleRoutePoints(points: List<LatLng>, count: Int): List<LatLng> {
        if (points.size <= count) return points
        val step = (points.size - 1).toDouble() / (count - 1)
        return (0 until count).map { i ->
            points[(i * step).toInt().coerceAtMost(points.size - 1)]
        }
    }

    /**
     * Adjust route duration based on traffic factor.
     * Uses a blended formula to cap the maximum increase at 50%.
     */
    fun adjustDurationForTraffic(
        durationSeconds: Long,
        trafficFactor: Double?,
        distanceKm: Double,
    ): Long? {
        if (trafficFactor == null) return null
        // Blend: moderate traffic impact instead of pure division
        val impact = (1.0 - trafficFactor).coerceIn(0.0, 0.5)
        return (durationSeconds * (1.0 + impact)).toLong()
    }
}
