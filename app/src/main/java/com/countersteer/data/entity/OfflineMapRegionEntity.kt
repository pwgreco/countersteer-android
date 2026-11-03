package com.countersteer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_map_regions")
data class OfflineMapRegionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "min_lat")
    val minLat: Double,

    @ColumnInfo(name = "max_lat")
    val maxLat: Double,

    @ColumnInfo(name = "min_lon")
    val minLon: Double,

    @ColumnInfo(name = "max_lon")
    val maxLon: Double,

    @ColumnInfo(name = "min_zoom", defaultValue = "6")
    val minZoom: Int = 6,

    @ColumnInfo(name = "max_zoom", defaultValue = "16")
    val maxZoom: Int = 16,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0,

    @ColumnInfo(name = "downloaded_at")
    val downloadedAt: Long,

    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: Long,

    @ColumnInfo(name = "maplibre_region_id")
    val maplibreRegionId: String,
)
