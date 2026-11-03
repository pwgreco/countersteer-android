package com.countersteer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.countersteer.data.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Query("SELECT * FROM saved_routes ORDER BY created_at DESC")
    fun getAll(): Flow<List<SavedRouteEntity>>

    @Query("SELECT * FROM saved_routes WHERE id = :id")
    suspend fun getById(id: Long): SavedRouteEntity?

    @Insert
    suspend fun insert(route: SavedRouteEntity): Long

    @Update
    suspend fun update(route: SavedRouteEntity)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM saved_routes WHERE is_favourite_ride = 1 ORDER BY created_at DESC")
    fun getFavouriteRides(): Flow<List<SavedRouteEntity>>

    @Query("""
        SELECT * FROM saved_routes
        WHERE is_favourite_ride = 1
        AND origin_lat BETWEEN :minLat AND :maxLat
        AND origin_lon BETWEEN :minLon AND :maxLon
        ORDER BY created_at DESC
    """)
    suspend fun getNearbyFavourites(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double,
    ): List<SavedRouteEntity>
}
