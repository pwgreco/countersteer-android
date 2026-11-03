package com.countersteer.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.countersteer.data.CountersteerDatabase
import com.countersteer.data.entity.TrustedContactEntity
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
class ContactDaoTest {

    private lateinit var db: CountersteerDatabase
    private lateinit var contactDao: ContactDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CountersteerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contactDao = db.contactDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetAll() = runTest {
        contactDao.insert(TrustedContactEntity(name = "Alice", phoneNumber = "+1234"))
        contactDao.insert(TrustedContactEntity(name = "Bob", phoneNumber = "+5678"))

        val contacts = contactDao.getAll().first()
        assertEquals(2, contacts.size)
    }

    @Test
    fun getPrimaryReturnsCorrectContact() = runTest {
        contactDao.insert(TrustedContactEntity(name = "Alice", phoneNumber = "+1234", isPrimary = false))
        contactDao.insert(TrustedContactEntity(name = "Bob", phoneNumber = "+5678", isPrimary = true))

        val primary = contactDao.getPrimary()
        assertNotNull(primary)
        assertEquals("Bob", primary!!.name)
    }

    @Test
    fun getPrimaryReturnsNullWhenNoPrimary() = runTest {
        contactDao.insert(TrustedContactEntity(name = "Alice", phoneNumber = "+1234", isPrimary = false))
        assertNull(contactDao.getPrimary())
    }

    @Test
    fun deleteRemovesContact() = runTest {
        val id = contactDao.insert(TrustedContactEntity(name = "Alice", phoneNumber = "+1234"))
        contactDao.delete(id)

        val contacts = contactDao.getAll().first()
        assertEquals(0, contacts.size)
    }
}
