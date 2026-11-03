package com.countersteer.data.offline

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.countersteer.data.dao.MapRegionDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.util.concurrent.TimeUnit

private const val TAG = "MapUpdateWorker"
private const val WORK_NAME = "offline_map_update"
private const val STALE_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

@HiltWorker
class MapUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val mapRegionDao: MapRegionDao,
    private val offlineMapManager: OfflineMapManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val regions = mapRegionDao.getAll().first()
            val now = System.currentTimeMillis()

            var updatedCount = 0
            for (region in regions) {
                val age = now - region.lastUpdatedAt
                if (age > STALE_THRESHOLD_MS) {
                    Log.i(TAG, "Updating stale region: ${region.name} (age: ${age / (24 * 60 * 60 * 1000)}d)")
                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(region.minLat, region.minLon))
                        .include(LatLng(region.maxLat, region.maxLon))
                        .build()
                    offlineMapManager.updateRegion(region)
                    updatedCount++
                }
            }

            Log.i(TAG, "Map update complete. Updated $updatedCount regions.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Map update failed", e)
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(false) // preferred but not required
                .build()

            val updateRequest = PeriodicWorkRequestBuilder<MapUpdateWorker>(
                1, TimeUnit.DAYS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                updateRequest,
            )

            Log.i(TAG, "Scheduled daily map update worker")
        }
    }
}
