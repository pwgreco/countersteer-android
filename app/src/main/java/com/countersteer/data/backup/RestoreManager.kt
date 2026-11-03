package com.countersteer.data.backup

import android.content.Context
import android.net.Uri
import com.countersteer.data.CountersteerDatabase
import com.countersteer.data.PreferencesRepository
import com.countersteer.data.dao.ContactDao
import com.countersteer.data.dao.FavoriteLocationDao
import com.countersteer.data.dao.MarkerDao
import com.countersteer.data.dao.RideDao
import com.countersteer.data.dao.RouteDao
import com.countersteer.data.dao.TrackPointDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class RestoreMode { MERGE, REPLACE }

@Singleton
class RestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CountersteerDatabase,
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

    suspend fun preview(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for URI")
            previewFromStream(inputStream)
        }
    }

    private fun previewFromStream(inputStream: InputStream): BackupPreview {
        val entries = readZipEntries(inputStream)

        val manifestJson = entries["manifest.json"]
            ?: throw IllegalStateException("Invalid backup: missing manifest.json")
        val manifest = json.decodeFromString<BackupManifest>(manifestJson)

        if (manifest.schemaVersion > BackupManifest.CURRENT_SCHEMA_VERSION) {
            throw IllegalStateException(
                "Backup version ${manifest.schemaVersion} is newer than supported version " +
                    "${BackupManifest.CURRENT_SCHEMA_VERSION}. Update the app first.",
            )
        }

        val rides = entries["rides.json"]?.let {
            json.decodeFromString<List<BackupRide>>(it)
        } ?: emptyList()

        val trackPoints = entries["track_points.json"]?.let {
            json.decodeFromString<List<BackupTrackPoint>>(it)
        } ?: emptyList()

        val routes = entries["saved_routes.json"]?.let {
            json.decodeFromString<List<BackupSavedRoute>>(it)
        } ?: emptyList()

        val contacts = entries["contacts.json"]?.let {
            json.decodeFromString<List<BackupContact>>(it)
        } ?: emptyList()

        val markers = entries["markers.json"]?.let {
            json.decodeFromString<List<BackupMarker>>(it)
        } ?: emptyList()

        val favoriteLocations = entries["favorite_locations.json"]?.let {
            json.decodeFromString<List<BackupFavoriteLocation>>(it)
        } ?: emptyList()

        val hasPrefs = entries.containsKey("preferences.json")

        return BackupPreview(
            ridesCount = rides.size,
            trackPointsCount = trackPoints.size,
            savedRoutesCount = routes.size,
            contactsCount = contacts.size,
            markersCount = markers.size,
            favoriteLocationsCount = favoriteLocations.size,
            hasPreferences = hasPrefs,
            exportDate = manifest.exportDate,
            schemaVersion = manifest.schemaVersion,
        )
    }

    suspend fun restore(uri: Uri, mode: RestoreMode): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for URI")
            restoreFromStream(inputStream, mode)
        }
    }

    private suspend fun restoreFromStream(inputStream: InputStream, mode: RestoreMode) {
        val entries = readZipEntries(inputStream)

        val manifestJson = entries["manifest.json"]
            ?: throw IllegalStateException("Invalid backup: missing manifest.json")
        val manifest = json.decodeFromString<BackupManifest>(manifestJson)

        if (manifest.schemaVersion > BackupManifest.CURRENT_SCHEMA_VERSION) {
            throw IllegalStateException(
                "Backup version ${manifest.schemaVersion} is newer than supported.",
            )
        }

        if (mode == RestoreMode.REPLACE) {
            deleteAllData()
            // Reset autoincrement sequences to avoid ID conflicts
            resetAutoIncrementSequences()
        }

        // In REPLACE mode, we can insert with original IDs since sequences are reset.
        // In MERGE mode, we need to track ID remapping for foreign keys.

        // Track old ride ID -> new ride ID mapping for MERGE mode track points
        val rideIdMap = mutableMapOf<Long, Long>()

        // Restore rides
        entries["rides.json"]?.let { ridesJson ->
            val rides = json.decodeFromString<List<BackupRide>>(ridesJson)
            for (ride in rides) {
                val entity = ride.toEntity()
                if (mode == RestoreMode.REPLACE) {
                    val newId = rideDao.insert(entity)
                    rideIdMap[entity.id] = newId
                } else {
                    // Merge: skip if exists
                    val existing = rideDao.getById(entity.id)
                    if (existing == null) {
                        val newId = rideDao.insert(entity.copy(id = 0))
                        rideIdMap[entity.id] = newId
                    }
                    // If ride already exists, skip its track points too (tracked below)
                }
            }
        }

        // Restore track points
        entries["track_points.json"]?.let { pointsJson ->
            val points = json.decodeFromString<List<BackupTrackPoint>>(pointsJson)
            // Insert in batches per ride, only for rides that were actually inserted
            val byRide = points.groupBy { it.rideId }
            for ((oldRideId, ridePoints) in byRide) {
                val newRideId = rideIdMap[oldRideId]
                if (newRideId != null) {
                    // Remap ride_id to new ID and reset point IDs
                    val entities = ridePoints.map {
                        it.toEntity().copy(id = 0, rideId = newRideId)
                    }
                    trackPointDao.insertAll(entities)
                }
                // If ride wasn't inserted (already existed in MERGE), skip its track points
            }
        }

        // Restore saved routes
        entries["saved_routes.json"]?.let { routesJson ->
            val routes = json.decodeFromString<List<BackupSavedRoute>>(routesJson)
            for (route in routes) {
                val entity = route.toEntity()
                if (mode == RestoreMode.REPLACE) {
                    routeDao.insert(entity.copy(id = 0))
                } else {
                    val existing = routeDao.getById(entity.id)
                    if (existing == null) {
                        routeDao.insert(entity.copy(id = 0))
                    }
                }
            }
        }

        // Restore contacts
        entries["contacts.json"]?.let { contactsJson ->
            val contacts = json.decodeFromString<List<BackupContact>>(contactsJson)
            for (contact in contacts) {
                val entity = contact.toEntity()
                if (mode == RestoreMode.REPLACE) {
                    contactDao.insert(entity.copy(id = 0))
                } else {
                    // For merge, try insert; skip on conflict by checking existence
                    try {
                        contactDao.insert(entity.copy(id = 0))
                    } catch (_: Exception) {
                        // Skip duplicate
                    }
                }
            }
        }

        // Restore markers
        entries["markers.json"]?.let { markersJson ->
            val markers = json.decodeFromString<List<BackupMarker>>(markersJson)
            for (marker in markers) {
                val entity = marker.toEntity()
                if (mode == RestoreMode.REPLACE) {
                    markerDao.insert(entity.copy(id = 0))
                } else {
                    try {
                        markerDao.insert(entity.copy(id = 0))
                    } catch (_: Exception) {
                        // Skip duplicate
                    }
                }
            }
        }

        // Restore favourite locations
        entries["favorite_locations.json"]?.let { favJson ->
            val favs = json.decodeFromString<List<BackupFavoriteLocation>>(favJson)
            for (fav in favs) {
                val entity = fav.toEntity()
                if (mode == RestoreMode.REPLACE) {
                    favoriteLocationDao.insert(entity.copy(id = 0))
                } else {
                    try {
                        favoriteLocationDao.insert(entity.copy(id = 0))
                    } catch (_: Exception) {
                        // Skip duplicate
                    }
                }
            }
        }

        // Restore preferences
        entries["preferences.json"]?.let { prefsJson ->
            val prefs = json.decodeFromString<BackupPreferences>(prefsJson)
            preferencesRepository.setCrashDetectionEnabled(prefs.crashDetectionEnabled)
            preferencesRepository.setCrashCountdownSeconds(prefs.crashCountdownSeconds)
            preferencesRepository.setSpeedAlertEnabled(prefs.speedAlertEnabled)
        }
    }

    private suspend fun deleteAllData() {
        // Delete in order respecting foreign keys
        val rides = rideDao.getAll().first()
        for (ride in rides) {
            trackPointDao.deleteByRideId(ride.id)
            rideDao.delete(ride.id)
        }

        val routes = routeDao.getAll().first()
        for (route in routes) {
            routeDao.delete(route.id)
        }

        val contacts = contactDao.getAll().first()
        for (contact in contacts) {
            contactDao.delete(contact.id)
        }

        val markers = markerDao.getAll().first()
        for (marker in markers) {
            markerDao.delete(marker.id)
        }

        val favorites = favoriteLocationDao.getAll().first()
        for (fav in favorites) {
            favoriteLocationDao.deleteById(fav.id)
        }
    }

    private fun resetAutoIncrementSequences() {
        // Reset SQLite autoincrement sequences after deleting all data
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM sqlite_sequence")
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
