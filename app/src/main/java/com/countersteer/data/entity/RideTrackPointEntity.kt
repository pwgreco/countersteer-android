package com.countersteer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ride_track_points",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["ride_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ride_id")],
)
data class RideTrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "ride_id")
    val rideId: Long,

    val timestamp: Long,

    val latitude: Double,

    val longitude: Double,

    @ColumnInfo(name = "altitude_m")
    val altitudeM: Double = 0.0,

    @ColumnInfo(name = "speed_kmh")
    val speedKmh: Double = 0.0,

    val bearing: Double = 0.0,

    @ColumnInfo(name = "accuracy_m")
    val accuracyM: Float = 0f,
)
