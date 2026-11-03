package com.countersteer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.countersteer.data.entity.FavoriteLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteLocationDao {

    @Query("SELECT * FROM favorite_locations ORDER BY name ASC")
    fun getAll(): Flow<List<FavoriteLocationEntity>>

    @Insert
    suspend fun insert(location: FavoriteLocationEntity): Long

    @Update
    suspend fun update(location: FavoriteLocationEntity)

    @Query("DELETE FROM favorite_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
