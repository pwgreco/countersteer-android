package com.countersteer.data.offline

import android.content.Context
import android.util.Log
import com.countersteer.data.dao.MapRegionDao
import com.countersteer.data.entity.OfflineMapRegionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow

private const val TAG = "OfflineMapManager"
private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val PIXEL_RATIO = 1.0f
private const val AVG_TILE_SIZE_BYTES = 15_000L // ~15KB average per vector tile

data class DownloadProgress(
    val regionName: String,
    val percentage: Float,
    val isComplete: Boolean,
    val error: String? = null,
)

@Singleton
class OfflineMapManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapRegionDao: MapRegionDao,
) {
    private val offlineManager: OfflineManager by lazy {
        OfflineManager.getInstance(context)
    }

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: Flow<DownloadProgress?> = _downloadProgress.asStateFlow()

    val regions: Flow<List<OfflineMapRegionEntity>> = mapRegionDao.getAll()

    fun getEstimatedSize(
        bounds: LatLngBounds,
        minZoom: Int = 6,
        maxZoom: Int = 16,
    ): Long {
        val totalTiles = estimateTileCount(bounds, minZoom, maxZoom)
        return totalTiles * AVG_TILE_SIZE_BYTES
    }

    private fun estimateTileCount(
        bounds: LatLngBounds,
        minZoom: Int,
        maxZoom: Int,
    ): Long {
        var totalTiles = 0L
        for (z in minZoom..maxZoom) {
            val n = (1 shl z).toDouble()
            val minTileX = lonToTileX(bounds.southWest.longitude, n)
            val maxTileX = lonToTileX(bounds.northEast.longitude, n)
            val minTileY = latToTileY(bounds.northEast.latitude, n) // North = smaller Y
            val maxTileY = latToTileY(bounds.southWest.latitude, n)
            val tilesX = (maxTileX - minTileX + 1).coerceAtLeast(1)
            val tilesY = (maxTileY - minTileY + 1).coerceAtLeast(1)
            totalTiles += tilesX * tilesY
        }
        return totalTiles
    }

    private fun lonToTileX(lon: Double, n: Double): Long {
        return ((lon + 180.0) / 360.0 * n).toLong()
    }

    private fun latToTileY(lat: Double, n: Double): Long {
        val latRad = Math.toRadians(lat)
        return ((1.0 - Math.log(Math.tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toLong()
    }

    suspend fun downloadRegion(
        name: String,
        bounds: LatLngBounds,
        minZoom: Int = 6,
        maxZoom: Int = 16,
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            _downloadProgress.value = DownloadProgress(name, 0f, false)

            val definition = OfflineTilePyramidRegionDefinition(
                DARK_STYLE_URL,
                bounds,
                minZoom.toDouble(),
                maxZoom.toDouble(),
                PIXEL_RATIO,
            )

            val metadata = name.toByteArray(Charsets.UTF_8)

            val offlineRegion = suspendCancellableCoroutine { cont ->
                offlineManager.createOfflineRegion(
                    definition,
                    metadata,
                    object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(region: OfflineRegion) {
                            cont.resume(region)
                        }

                        override fun onError(error: String) {
                            cont.resumeWithException(RuntimeException(error))
                        }
                    },
                )
            }

            val regionId = offlineRegion.id.toString()
            val now = System.currentTimeMillis()
            val estimatedSize = getEstimatedSize(bounds, minZoom, maxZoom)

            val entity = OfflineMapRegionEntity(
                name = name,
                minLat = bounds.southWest.latitude,
                maxLat = bounds.northEast.latitude,
                minLon = bounds.southWest.longitude,
                maxLon = bounds.northEast.longitude,
                minZoom = minZoom,
                maxZoom = maxZoom,
                sizeBytes = estimatedSize,
                downloadedAt = now,
                lastUpdatedAt = now,
                maplibreRegionId = regionId,
            )
            val dbId = mapRegionDao.insert(entity)

            // Set observer for progress
            offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    val percentage = if (status.requiredResourceCount > 0) {
                        (status.completedResourceCount.toFloat() /
                            status.requiredResourceCount.toFloat() * 100f)
                    } else {
                        0f
                    }

                    _downloadProgress.value = DownloadProgress(
                        regionName = name,
                        percentage = percentage,
                        isComplete = status.isComplete,
                    )

                    if (status.isComplete) {
                        Log.i(TAG, "Download complete for region: $name, size: ${status.completedResourceSize}")
                    }
                }

                override fun onError(error: OfflineRegionError) {
                    Log.e(TAG, "Download error for region $name: ${error.reason} - ${error.message}")
                    _downloadProgress.value = DownloadProgress(
                        regionName = name,
                        percentage = 0f,
                        isComplete = false,
                        error = "${error.reason}: ${error.message}",
                    )
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    Log.e(TAG, "Tile count limit exceeded for region $name: $limit")
                    _downloadProgress.value = DownloadProgress(
                        regionName = name,
                        percentage = 0f,
                        isComplete = false,
                        error = "Tile count limit exceeded ($limit tiles)",
                    )
                }
            })

            // Start download
            offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)

            Result.success(dbId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download region: $name", e)
            _downloadProgress.value = DownloadProgress(name, 0f, false, e.message)
            Result.failure(e)
        }
    }

    suspend fun deleteRegion(entity: OfflineMapRegionEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val regions = suspendCancellableCoroutine { cont ->
                    offlineManager.listOfflineRegions(
                        object : OfflineManager.ListOfflineRegionsCallback {
                            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                                cont.resume(offlineRegions ?: emptyArray())
                            }

                            override fun onError(error: String) {
                                cont.resumeWithException(RuntimeException(error))
                            }
                        },
                    )
                }

                val targetId = entity.maplibreRegionId.toLongOrNull()
                val offlineRegion = regions.find { it.id == targetId }

                if (offlineRegion != null) {
                    suspendCancellableCoroutine { cont ->
                        offlineRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {
                                cont.resume(Unit)
                            }

                            override fun onError(error: String) {
                                cont.resumeWithException(RuntimeException(error))
                            }
                        })
                    }
                }

                mapRegionDao.delete(entity.id)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete region: ${entity.name}", e)
                Result.failure(e)
            }
        }

    suspend fun updateRegion(entity: OfflineMapRegionEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val bounds = LatLngBounds.Builder()
                    .include(org.maplibre.android.geometry.LatLng(entity.minLat, entity.minLon))
                    .include(org.maplibre.android.geometry.LatLng(entity.maxLat, entity.maxLon))
                    .build()

                // Delete old and re-download
                deleteRegion(entity)
                downloadRegion(entity.name, bounds, entity.minZoom, entity.maxZoom)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update region: ${entity.name}", e)
                Result.failure(e)
            }
        }

    fun clearProgress() {
        _downloadProgress.value = null
    }
}
