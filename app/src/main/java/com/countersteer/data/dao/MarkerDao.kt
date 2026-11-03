package com.countersteer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.countersteer.data.entity.MarkerType
import com.countersteer.data.entity.RoadMarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {

    @Query("SELECT * FROM road_markers")
    fun getAll(): Flow<List<RoadMarkerEntity>>

    @Query("SELECT * FROM road_markers WHERE type = :type")
    fun getByType(type: MarkerType): Flow<List<RoadMarkerEntity>>

    // Bounding-box approximation: uses a constant degrees-per-metre factor (1/111000)
    // with a cos(lat) correction for longitude. This breaks at high latitudes (>70°)
    // where the cosine term distorts significantly, and near the antimeridian (±180°
    // longitude) where the BETWEEN clause wraps incorrectly. Acceptable for typical
    // Canadian riding latitudes (~42°–60° N).
    @Query(
        """
        SELECT * FROM road_markers
        WHERE latitude BETWEEN :lat - (:radiusM / 111000.0) AND :lat + (:radiusM / 111000.0)
        AND longitude BETWEEN :lon - (:radiusM / (111000.0 * COS(:lat * 3.14159265 / 180.0)))
            AND :lon + (:radiusM / (111000.0 * COS(:lat * 3.14159265 / 180.0)))
        """
    )
    fun getNearby(lat: Double, lon: Double, radiusM: Double): Flow<List<RoadMarkerEntity>>

    @Insert
    suspend fun insert(marker: RoadMarkerEntity): Long

    @Update
    suspend fun update(marker: RoadMarkerEntity)

    @Query("DELETE FROM road_markers WHERE id = :id")
    suspend fun delete(id: Long)
}
