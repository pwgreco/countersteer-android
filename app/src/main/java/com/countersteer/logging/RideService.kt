package com.countersteer.logging

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.countersteer.R
import com.countersteer.app.MainActivity
import com.countersteer.data.entity.RouteType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RideService : Service() {

    companion object {
        const val CHANNEL_ID = "ride_recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.countersteer.action.START_RIDE"
        const val ACTION_STOP = "com.countersteer.action.STOP_RIDE"
        const val EXTRA_ROUTE_TYPE = "route_type"

        fun startRide(context: Context, routeType: RouteType) {
            val intent = Intent(context, RideService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROUTE_TYPE, routeType.name)
            }
            context.startForegroundService(intent)
        }

        fun stopRide(context: Context) {
            val intent = Intent(context, RideService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var rideLogger: RideLogger

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationUpdateJob: Job? = null

    private val binder = RideBinder()

    inner class RideBinder : Binder() {
        fun getService(): RideService = this@RideService
        fun getRideLogger(): RideLogger = rideLogger
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val routeTypeName = intent.getStringExtra(EXTRA_ROUTE_TYPE) ?: RouteType.QUICKEST.name
                val routeType = try {
                    RouteType.valueOf(routeTypeName)
                } catch (_: Exception) {
                    RouteType.QUICKEST
                }
                startRecording(routeType)
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording(routeType: RouteType) {
        val notification = buildNotification("Recording ride...", "0.0 km | 00:00")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        rideLogger.startRide(routeType)

        // Periodically update notification
        notificationUpdateJob = serviceScope.launch {
            while (true) {
                delay(5_000)
                val progress = rideLogger.progress.value
                if (progress.isRecording) {
                    val distanceText = "%.1f km".format(progress.distanceKm)
                    val durationText = formatDuration(progress.durationSeconds)
                    val notification = buildNotification(
                        "Recording ride...",
                        "$distanceText | $durationText",
                    )
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun stopRecording() {
        notificationUpdateJob?.cancel()
        serviceScope.launch {
            rideLogger.stopRide()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ride Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows ride recording status"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // App was swiped away — save partial ride data
        if (rideLogger.isRecording()) {
            serviceScope.launch {
                try {
                    rideLogger.stopRide()
                } catch (_: Exception) {
                    // Best effort save
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationUpdateJob?.cancel()
        serviceScope.cancel()
    }
}
