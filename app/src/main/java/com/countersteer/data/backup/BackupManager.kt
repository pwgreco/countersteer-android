package com.countersteer.data.backup

import android.content.Context
import android.net.Uri
import com.countersteer.data.PreferencesRepository
import com.countersteer.data.dao.ContactDao
import com.countersteer.data.dao.FavoriteLocationDao
import com.countersteer.data.dao.MarkerDao
import com.countersteer.data.dao.RideDao
import com.countersteer.data.dao.RouteDao
import com.countersteer.data.dao.TrackPointDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rideDao: RideDao,
    private val trackPointDao: TrackPointDao,
    private val contactDao: ContactDao,
    private val routeDao: RouteDao,
    private val markerDao: MarkerDao,
    private val favoriteLocationDao: FavoriteLocationDao,
    private val preferencesRepository: PreferencesRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Cannot open output stream for URI")
            exportToStream(outputStream)
        }
    }

    private suspend fun exportToStream(outputStream: OutputStream) {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val appVersion = packageInfo?.versionName ?: "unknown"

        ZipOutputStream(outputStream).use { zip ->
            // Manifest
            val manifest = BackupManifest(
                appVersion = appVersion,
            )
            writeEntry(zip, "manifest.json", json.encodeToString(manifest))

            // Rides
            val rides = rideDao.getAll().first()
            writeEntry(zip, "rides.json", json.encodeToString(rides.map { it.toBackup() }))

            // Track points - collect for all rides
            val allTrackPoints = mutableListOf<BackupTrackPoint>()
            for (ride in rides) {
                val points = trackPointDao.getByRideId(ride.id).first()
                allTrackPoints.addAll(points.map { it.toBackup() })
            }
            writeEntry(zip, "track_points.json", json.encodeToString(allTrackPoints))

            // Saved routes
            val routes = routeDao.getAll().first()
            writeEntry(zip, "saved_routes.json", json.encodeToString(routes.map { it.toBackup() }))

            // Contacts
            val contacts = contactDao.getAll().first()
            writeEntry(zip, "contacts.json", json.encodeToString(contacts.map { it.toBackup() }))

            // Markers
            val markers = markerDao.getAll().first()
            writeEntry(zip, "markers.json", json.encodeToString(markers.map { it.toBackup() }))

            // Favourite locations
            val favorites = favoriteLocationDao.getAll().first()
            writeEntry(zip, "favorite_locations.json", json.encodeToString(favorites.map { it.toBackup() }))

            // Preferences (exclude API keys)
            val prefs = BackupPreferences(
                crashDetectionEnabled = preferencesRepository.crashDetectionEnabled.first(),
                crashCountdownSeconds = preferencesRepository.crashCountdownSeconds.first(),
                speedAlertEnabled = preferencesRepository.speedAlertEnabled.first(),
            )
            writeEntry(zip, "preferences.json", json.encodeToString(prefs))
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
