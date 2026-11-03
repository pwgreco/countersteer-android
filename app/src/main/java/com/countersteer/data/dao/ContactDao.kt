package com.countersteer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.countersteer.data.entity.TrustedContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM trusted_contacts")
    fun getAll(): Flow<List<TrustedContactEntity>>

    @Query("SELECT * FROM trusted_contacts ORDER BY is_primary DESC")
    suspend fun getAllOnce(): List<TrustedContactEntity>

    @Query("SELECT * FROM trusted_contacts WHERE is_primary = 1 LIMIT 1")
    suspend fun getPrimary(): TrustedContactEntity?

    @Insert
    suspend fun insert(contact: TrustedContactEntity): Long

    @Update
    suspend fun update(contact: TrustedContactEntity)

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE trusted_contacts SET is_primary = 0")
    suspend fun clearAllPrimary()

    @Query("UPDATE trusted_contacts SET is_primary = 1 WHERE id = :contactId")
    suspend fun markPrimary(contactId: Long)

    @Transaction
    suspend fun setPrimary(contactId: Long) {
        clearAllPrimary()
        markPrimary(contactId)
    }

    @Transaction
    suspend fun clearAndInsertPrimary(contact: TrustedContactEntity): Long {
        clearAllPrimary()
        return insert(contact)
    }
}
