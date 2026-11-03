package com.countersteer.data.backup

import com.countersteer.data.entity.FavoriteLocationEntity
import com.countersteer.data.entity.MarkerSource
import com.countersteer.data.entity.MarkerType
import com.countersteer.data.entity.RideEntity
import com.countersteer.data.entity.RideTrackPointEntity
import com.countersteer.data.entity.RoadMarkerEntity
import com.countersteer.data.entity.RouteType
import com.countersteer.data.entity.SavedRouteEntity
import com.countersteer.data.entity.TrustedContactEntity
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportDate: Long = System.currentTimeMillis(),
    val appVersion: String = "",
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupRide(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceKm: Double = 0.0,
    val durationSeconds: Long = 0,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val curvatureScore: Double = 0.0,
    val routeType: String = "QUICKEST",
    val routePolyline: String = "",
)

@Serializable
data class BackupTrackPoint(
    val id: Long,
    val rideId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double = 0.0,
    val speedKmh: Double = 0.0,
    val bearing: Double = 0.0,
    val accuracyM: Float = 0f,
)

@Serializable
data class BackupSavedRoute(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double? = null,
    val destinationLon: Double? = null,
    val routePolyline: String,
    val routeType: String,
    val distanceKm: Double,
    val valhallaJson: String = "",
)

@Serializable
data class BackupContact(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val isPrimary: Boolean = false,
)

@Serializable
data class BackupMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val note: String? = null,
    val source: String = "USER",
    val createdAt: Long,
    val alertRadiusM: Int = 1000,
)

@Serializable
data class BackupFavoriteLocation(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long,
)

@Serializable
data class BackupPreferences(
    val crashDetectionEnabled: Boolean = true,
    val crashCountdownSeconds: Int = 30,
    val speedAlertEnabled: Boolean = true,
)

data class BackupPreview(
    val ridesCount: Int = 0,
    val trackPointsCount: Int = 0,
    val savedRoutesCount: Int = 0,
    val contactsCount: Int = 0,
    val markersCount: Int = 0,
    val favoriteLocationsCount: Int = 0,
    val hasPreferences: Boolean = false,
    val exportDate: Long = 0,
    val schemaVersion: Int = 0,
)

// Entity -> Backup model converters

fun RideEntity.toBackup() = BackupRide(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    avgSpeedKmh = avgSpeedKmh,
    maxSpeedKmh = maxSpeedKmh,
    elevationGainM = elevationGainM,
    curvatureScore = curvatureScore,
    routeType = routeType.name,
    routePolyline = routePolyline,
)

fun BackupRide.toEntity() = RideEntity(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    avgSpeedKmh = avgSpeedKmh,
    maxSpeedKmh = maxSpeedKmh,
    elevationGainM = elevationGainM,
    curvatureScore = curvatureScore,
    routeType = try { RouteType.valueOf(routeType) } catch (_: Exception) { RouteType.QUICKEST },
    routePolyline = routePolyline,
)

fun RideTrackPointEntity.toBackup() = BackupTrackPoint(
    id = id,
    rideId = rideId,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    altitudeM = altitudeM,
    speedKmh = speedKmh,
    bearing = bearing,
    accuracyM = accuracyM,
)

fun BackupTrackPoint.toEntity() = RideTrackPointEntity(
    id = id,
    rideId = rideId,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    altitudeM = altitudeM,
    speedKmh = speedKmh,
    bearing = bearing,
    accuracyM = accuracyM,
)

fun SavedRouteEntity.toBackup() = BackupSavedRoute(
    id = id,
    name = name,
    createdAt = createdAt,
    originLat = originLat,
    originLon = originLon,
    destinationLat = destinationLat,
    destinationLon = destinationLon,
    routePolyline = routePolyline,
    routeType = routeType.name,
    distanceKm = distanceKm,
    valhallaJson = valhallaJson,
)

fun BackupSavedRoute.toEntity() = SavedRouteEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    originLat = originLat,
    originLon = originLon,
    destinationLat = destinationLat,
    destinationLon = destinationLon,
    routePolyline = routePolyline,
    routeType = try { RouteType.valueOf(routeType) } catch (_: Exception) { RouteType.QUICKEST },
    distanceKm = distanceKm,
    valhallaJson = valhallaJson,
)

fun TrustedContactEntity.toBackup() = BackupContact(
    id = id,
    name = name,
    phoneNumber = phoneNumber,
    isPrimary = isPrimary,
)

fun BackupContact.toEntity() = TrustedContactEntity(
    id = id,
    name = name,
    phoneNumber = phoneNumber,
    isPrimary = isPrimary,
)

fun RoadMarkerEntity.toBackup() = BackupMarker(
    id = id,
    latitude = latitude,
    longitude = longitude,
    type = type.name,
    note = note,
    source = source.name,
    createdAt = createdAt,
    alertRadiusM = alertRadiusM,
)

fun BackupMarker.toEntity() = RoadMarkerEntity(
    id = id,
    latitude = latitude,
    longitude = longitude,
    type = try { MarkerType.valueOf(type) } catch (_: Exception) { MarkerType.CUSTOM },
    note = note,
    source = try { MarkerSource.valueOf(source) } catch (_: Exception) { MarkerSource.USER },
    createdAt = createdAt,
    alertRadiusM = alertRadiusM,
)

fun FavoriteLocationEntity.toBackup() = BackupFavoriteLocation(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
)

fun BackupFavoriteLocation.toEntity() = FavoriteLocationEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
)
