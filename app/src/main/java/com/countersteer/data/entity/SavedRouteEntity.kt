package com.countersteer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "origin_lat")
    val originLat: Double,

    @ColumnInfo(name = "origin_lon")
    val originLon: Double,

    @ColumnInfo(name = "destination_lat")
    val destinationLat: Double? = null,

    @ColumnInfo(name = "destination_lon")
    val destinationLon: Double? = null,

    @ColumnInfo(name = "route_polyline")
    val routePolyline: String,

    @ColumnInfo(name = "route_type")
    val routeType: RouteType,

    @ColumnInfo(name = "distance_km")
    val distanceKm: Double,

    @ColumnInfo(name = "valhalla_json")
    val valhallaJson: String = "",

    @ColumnInfo(name = "is_favourite_ride", defaultValue = "0")
    val isFavouriteRide: Boolean = false,
)
