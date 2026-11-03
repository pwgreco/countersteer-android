package com.countersteer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        const val TAG = "SecurePrefsRepo"
        const val FILE_NAME = "secure_prefs"
        const val KEY_TOMTOM_API_KEY = "tomtom_api_key"
    }

    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Failed to create encrypted preferences", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create encrypted preferences", e)
            null
        }
    }

    fun getTomTomApiKey(): String? = prefs?.getString(KEY_TOMTOM_API_KEY, null)

    fun setTomTomApiKey(key: String) {
        prefs?.edit()?.putString(KEY_TOMTOM_API_KEY, key)?.apply()
    }

    fun clearTomTomApiKey() {
        prefs?.edit()?.remove(KEY_TOMTOM_API_KEY)?.apply()
    }
}
