package com.countersteer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,

    @ColumnInfo(name = "distance_km")
    val distanceKm: Double = 0.0,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long = 0,

    @ColumnInfo(name = "avg_speed_kmh")
    val avgSpeedKmh: Double = 0.0,

    @ColumnInfo(name = "max_speed_kmh")
    val maxSpeedKmh: Double = 0.0,

    @ColumnInfo(name = "elevation_gain_m")
    val elevationGainM: Double = 0.0,

    @ColumnInfo(name = "curvature_score")
    val curvatureScore: Double = 0.0,

    @ColumnInfo(name = "route_type")
    val routeType: RouteType = RouteType.QUICKEST,

    @ColumnInfo(name = "route_polyline")
    val routePolyline: String = "",
)
