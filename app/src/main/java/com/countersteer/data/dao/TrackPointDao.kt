package com.countersteer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.countersteer.data.entity.RideTrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {

    @Query("SELECT * FROM ride_track_points WHERE ride_id = :rideId ORDER BY timestamp ASC")
    fun getByRideId(rideId: Long): Flow<List<RideTrackPointEntity>>

    @Insert
    suspend fun insertAll(points: List<RideTrackPointEntity>)

    @Query("DELETE FROM ride_track_points WHERE ride_id = :rideId")
    suspend fun deleteByRideId(rideId: Long)
}
