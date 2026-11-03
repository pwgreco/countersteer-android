package com.countersteer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "road_markers")
data class RoadMarkerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val latitude: Double,

    val longitude: Double,

    val type: MarkerType,

    val note: String? = null,

    val source: MarkerSource = MarkerSource.USER,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "alert_radius_m", defaultValue = "1000")
    val alertRadiusM: Int = 1000,
)
