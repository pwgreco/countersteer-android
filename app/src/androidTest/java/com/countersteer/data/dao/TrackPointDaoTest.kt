package com.countersteer.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.countersteer.data.CountersteerDatabase
import com.countersteer.data.entity.RideEntity
import com.countersteer.data.entity.RideTrackPointEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackPointDaoTest {

    private lateinit var db: CountersteerDatabase
    private lateinit var rideDao: RideDao
    private lateinit var trackPointDao: TrackPointDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CountersteerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rideDao = db.rideDao()
        trackPointDao = db.trackPointDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAllAndGetByRideId() = runTest {
        val rideId = rideDao.insert(RideEntity(startedAt = 1000L))
        val points = listOf(
            RideTrackPointEntity(rideId = rideId, timestamp = 1000L, latitude = 48.0, longitude = 11.0),
            RideTrackPointEntity(rideId = rideId, timestamp = 2000L, latitude = 48.1, longitude = 11.1),
        )
        trackPointDao.insertAll(points)

        val loaded = trackPointDao.getByRideId(rideId).first()
        assertEquals(2, loaded.size)
    }

    @Test
    fun cascadeDeleteRemovesTrackPoints() = runTest {
        val rideId = rideDao.insert(RideEntity(startedAt = 1000L))
        trackPointDao.insertAll(
            listOf(RideTrackPointEntity(rideId = rideId, timestamp = 1000L, latitude = 48.0, longitude = 11.0))
        )
        rideDao.delete(rideId)

        val loaded = trackPointDao.getByRideId(rideId).first()
        assertEquals(0, loaded.size)
    }
}
