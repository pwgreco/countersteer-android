package com.countersteer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.countersteer.data.dao.ContactDao
import com.countersteer.data.dao.FavoriteLocationDao
import com.countersteer.data.dao.MapRegionDao
import com.countersteer.data.dao.MarkerDao
import com.countersteer.data.dao.RideDao
import com.countersteer.data.dao.RouteDao
import com.countersteer.data.dao.TrackPointDao
import com.countersteer.data.entity.FavoriteLocationEntity
import com.countersteer.data.entity.OfflineMapRegionEntity
import com.countersteer.data.entity.RideEntity
import com.countersteer.data.entity.RideTrackPointEntity
import com.countersteer.data.entity.RoadMarkerEntity
import com.countersteer.data.entity.SavedRouteEntity
import com.countersteer.data.entity.TrustedContactEntity

@Database(
    entities = [
        RideEntity::class,
        RideTrackPointEntity::class,
        TrustedContactEntity::class,
        SavedRouteEntity::class,
        OfflineMapRegionEntity::class,
        RoadMarkerEntity::class,
        FavoriteLocationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CountersteerDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun contactDao(): ContactDao
    abstract fun routeDao(): RouteDao
    abstract fun mapRegionDao(): MapRegionDao
    abstract fun markerDao(): MarkerDao
    abstract fun favoriteLocationDao(): FavoriteLocationDao
}
