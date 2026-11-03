package com.countersteer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.countersteer.data.entity.OfflineMapRegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MapRegionDao {

    @Query("SELECT * FROM offline_map_regions")
    fun getAll(): Flow<List<OfflineMapRegionEntity>>

    @Insert
    suspend fun insert(region: OfflineMapRegionEntity): Long

    @Update
    suspend fun update(region: OfflineMapRegionEntity)

    @Query("DELETE FROM offline_map_regions WHERE id = :id")
    suspend fun delete(id: Long)
}
