package com.countersteer.routing.model

import com.countersteer.data.entity.RouteType

data class Route(
    val type: RouteType,
    val polyline: String,
    val points: List<LatLng>,
    val distanceKm: Double,
    val durationSeconds: Long,
    val durationWithTrafficSeconds: Long?,
    val curvatureScore: Double,
    val scenicScore: Double,
    val maneuvers: List<Maneuver>,
    val speedLimits: List<SpeedLimit>,
    val hasUnpavedSegments: Boolean,
    val isOutAndBack: Boolean = false,
)

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

data class Maneuver(
    val instruction: String,
    val type: Int,
    val streetName: String,
    val distanceKm: Double,
    val latitude: Double,
    val longitude: Double,
)

data class SpeedLimit(
    val speedKmh: Int,
    val startIndex: Int,
    val endIndex: Int,
)
