package com.countersteer.alerts

import androidx.compose.runtime.Immutable
import com.countersteer.data.entity.MarkerType

/**
 * Information about an active road alert displayed to the rider.
 */
@Immutable
data class AlertInfo(
    val markerId: Long,
    val type: MarkerType,
    val distanceMeters: Int,
    val note: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String
        get() = when (type) {
            MarkerType.SPEED_TRAP -> "Speed Camera"
            MarkerType.HAZARD -> "Hazard"
            MarkerType.PHOTO_SPOT -> "Photo Spot"
            MarkerType.CUSTOM -> "Marker"
            MarkerType.SPEED_CAMERA -> "Speed Camera"
            MarkerType.RED_LIGHT_CAMERA -> "Red Light Camera"
        }
}
