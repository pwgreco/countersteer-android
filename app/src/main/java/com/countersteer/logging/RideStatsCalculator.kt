package com.countersteer.logging

import com.countersteer.data.entity.RideTrackPointEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class RideStats(
    val distanceKm: Double,
    val durationSeconds: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainM: Double,
    val curvatureScore: Double,
)

@Singleton
class RideStatsCalculator @Inject constructor() {

    fun calculate(points: List<RideTrackPointEntity>): RideStats {
        if (points.isEmpty()) {
            return RideStats(0.0, 0, 0.0, 0.0, 0.0, 0.0)
        }

        val durationSeconds = if (points.size >= 2) {
            (points.last().timestamp - points.first().timestamp) / 1000
        } else {
            0L
        }

        var totalDistanceKm = 0.0
        var maxSpeedKmh = 0.0
        var elevationGainM = 0.0
        var totalAngleChange = 0.0
        var turnCount = 0

        for (i in points.indices) {
            val point = points[i]

            if (point.speedKmh > maxSpeedKmh) {
                maxSpeedKmh = point.speedKmh
            }

            if (i > 0) {
                val prev = points[i - 1]
                totalDistanceKm += haversineKm(
                    prev.latitude, prev.longitude,
                    point.latitude, point.longitude,
                )

                // Elevation gain (only positive differences)
                val altDiff = point.altitudeM - prev.altitudeM
                if (altDiff > 0) {
                    elevationGainM += altDiff
                }
            }

            // Curvature: bearing changes between consecutive segments
            if (i in 1 until points.size - 1) {
                val bearing1 = bearing(
                    points[i - 1].latitude, points[i - 1].longitude,
                    point.latitude, point.longitude,
                )
                val bearing2 = bearing(
                    point.latitude, point.longitude,
                    points[i + 1].latitude, points[i + 1].longitude,
                )
                var angleDiff = abs(bearing2 - bearing1)
                if (angleDiff > 180.0) angleDiff = 360.0 - angleDiff

                if (angleDiff > 5.0) {
                    totalAngleChange += angleDiff
                    turnCount++
                }
            }
        }

        val avgSpeedKmh = if (durationSeconds > 0) {
            totalDistanceKm / (durationSeconds / 3600.0)
        } else {
            0.0
        }

        val curvatureScore = if (turnCount > 0 && points.size >= 3) {
            val avgAngle = totalAngleChange / turnCount
            val turnFrequency = turnCount.toDouble() / (points.size - 2)
            val angleScore = min(avgAngle / 45.0, 1.0) * 60.0
            val frequencyScore = min(turnFrequency / 0.3, 1.0) * 40.0
            (angleScore + frequencyScore).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        return RideStats(
            distanceKm = totalDistanceKm,
            durationSeconds = durationSeconds,
            avgSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            elevationGainM = elevationGainM,
            curvatureScore = curvatureScore,
        )
    }

    private fun haversineKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun bearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}
