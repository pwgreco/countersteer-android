package com.countersteer.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.countersteer.data.CountersteerDatabase
import com.countersteer.data.entity.RoadMarkerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkerDaoTest {

    private lateinit var db: CountersteerDatabase
    private lateinit var markerDao: MarkerDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CountersteerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        markerDao = db.markerDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetAll() = runTest {
        markerDao.insert(RoadMarkerEntity(latitude = 48.0, longitude = 11.0, type = "hazard", createdAt = 1000L))
        markerDao.insert(RoadMarkerEntity(latitude = 49.0, longitude = 12.0, type = "speed_trap", createdAt = 2000L))

        val markers = markerDao.getAll().first()
        assertEquals(2, markers.size)
    }

    @Test
    fun getByTypeFiltersCorrectly() = runTest {
        markerDao.insert(RoadMarkerEntity(latitude = 48.0, longitude = 11.0, type = "hazard", createdAt = 1000L))
        markerDao.insert(RoadMarkerEntity(latitude = 49.0, longitude = 12.0, type = "speed_trap", createdAt = 2000L))
        markerDao.insert(RoadMarkerEntity(latitude = 48.5, longitude = 11.5, type = "hazard", createdAt = 3000L))

        val hazards = markerDao.getByType("hazard").first()
        assertEquals(2, hazards.size)

        val traps = markerDao.getByType("speed_trap").first()
        assertEquals(1, traps.size)
    }

    @Test
    fun deleteRemovesMarker() = runTest {
        val id = markerDao.insert(RoadMarkerEntity(latitude = 48.0, longitude = 11.0, type = "hazard", createdAt = 1000L))
        markerDao.delete(id)

        val markers = markerDao.getAll().first()
        assertEquals(0, markers.size)
    }
}
