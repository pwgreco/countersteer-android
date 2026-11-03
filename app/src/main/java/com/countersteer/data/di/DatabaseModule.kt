package com.countersteer.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.countersteer.data.CountersteerDatabase
import com.countersteer.data.dao.ContactDao
import com.countersteer.data.dao.FavoriteLocationDao
import com.countersteer.data.dao.MapRegionDao
import com.countersteer.data.dao.MarkerDao
import com.countersteer.data.dao.RideDao
import com.countersteer.data.dao.RouteDao
import com.countersteer.data.dao.TrackPointDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_routes ADD COLUMN is_favourite_ride INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CountersteerDatabase =
        Room.databaseBuilder(
            context,
            CountersteerDatabase::class.java,
            "countersteer.db",
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .addMigrations(MIGRATION_2_3)
            .build()

    @Provides
    fun provideRideDao(db: CountersteerDatabase): RideDao = db.rideDao()

    @Provides
    fun provideTrackPointDao(db: CountersteerDatabase): TrackPointDao = db.trackPointDao()

    @Provides
    fun provideContactDao(db: CountersteerDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideRouteDao(db: CountersteerDatabase): RouteDao = db.routeDao()

    @Provides
    fun provideMapRegionDao(db: CountersteerDatabase): MapRegionDao = db.mapRegionDao()

    @Provides
    fun provideMarkerDao(db: CountersteerDatabase): MarkerDao = db.markerDao()

    @Provides
    fun provideFavoriteLocationDao(db: CountersteerDatabase): FavoriteLocationDao =
        db.favoriteLocationDao()
}
