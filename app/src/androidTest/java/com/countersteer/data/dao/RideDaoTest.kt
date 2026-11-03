package com.countersteer.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.countersteer.data.CountersteerDatabase
import com.countersteer.data.entity.RideEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RideDaoTest {

    private lateinit var db: CountersteerDatabase
    private lateinit var rideDao: RideDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CountersteerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rideDao = db.rideDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runTest {
        val ride = RideEntity(startedAt = 1000L, distanceKm = 42.0)
        val id = rideDao.insert(ride)
        val loaded = rideDao.getById(id)
        assertNotNull(loaded)
        assertEquals(42.0, loaded!!.distanceKm, 0.01)
    }

    @Test
    fun getAllReturnsDescByStartedAt() = runTest {
        rideDao.insert(RideEntity(startedAt = 1000L))
        rideDao.insert(RideEntity(startedAt = 3000L))
        rideDao.insert(RideEntity(startedAt = 2000L))

        val rides = rideDao.getAll().first()
        assertEquals(3, rides.size)
        assertEquals(3000L, rides[0].startedAt)
        assertEquals(2000L, rides[1].startedAt)
        assertEquals(1000L, rides[2].startedAt)
    }

    @Test
    fun deleteRemovesRide() = runTest {
        val id = rideDao.insert(RideEntity(startedAt = 1000L))
        rideDao.delete(id)
        assertNull(rideDao.getById(id))
    }
}
