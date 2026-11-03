package com.countersteer.navigation

import androidx.compose.runtime.Immutable
import com.countersteer.routing.model.Maneuver
import com.countersteer.routing.model.Route

@Immutable
data class NavigationState(
    val route: Route? = null,
    val isNavigating: Boolean = false,
    val currentManeuver: Maneuver? = null,
    val nextManeuver: Maneuver? = null,
    val distanceToNextManeuverKm: Double = 0.0,
    val currentSpeedKmh: Int = 0,
    val currentSpeedLimitKmh: Int? = null,
    val distanceRemainingKm: Double = 0.0,
    val etaSeconds: Long = 0L,
    val isOffRoute: Boolean = false,
    val currentBearing: Float = 0f,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val currentManeuverIndex: Int = 0,
    val currentPointIndex: Int = 0,
)

enum class ManeuverIcon {
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    U_TURN_LEFT,
    U_TURN_RIGHT,
    ARRIVE,
    DEPART,
    ROUNDABOUT,
    MERGE,
    RAMP,
    UNKNOWN,
}

/**
 * Maps Valhalla maneuver type integers to our icon enum.
 * Valhalla types: https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/
 */
fun maneuverTypeToIcon(type: Int): ManeuverIcon = when (type) {
    0 -> ManeuverIcon.STRAIGHT      // kNone
    1 -> ManeuverIcon.DEPART        // kStart
    2 -> ManeuverIcon.DEPART        // kStartRight
    3 -> ManeuverIcon.DEPART        // kStartLeft
    4 -> ManeuverIcon.ARRIVE        // kDestination
    5 -> ManeuverIcon.ARRIVE        // kDestinationRight
    6 -> ManeuverIcon.ARRIVE        // kDestinationLeft
    7 -> ManeuverIcon.STRAIGHT      // kBecomes
    8 -> ManeuverIcon.STRAIGHT      // kContinue
    9 -> ManeuverIcon.SLIGHT_RIGHT  // kSlightRight
    10 -> ManeuverIcon.TURN_RIGHT   // kRight
    11 -> ManeuverIcon.SHARP_RIGHT  // kSharpRight
    12 -> ManeuverIcon.U_TURN_RIGHT // kUturnRight
    13 -> ManeuverIcon.U_TURN_LEFT  // kUturnLeft
    14 -> ManeuverIcon.SHARP_LEFT   // kSharpLeft
    15 -> ManeuverIcon.TURN_LEFT    // kLeft
    16 -> ManeuverIcon.SLIGHT_LEFT  // kSlightLeft
    17 -> ManeuverIcon.SLIGHT_RIGHT // kRampStraight
    18 -> ManeuverIcon.RAMP         // kRampRight
    19 -> ManeuverIcon.RAMP         // kRampLeft
    20 -> ManeuverIcon.RAMP         // kExitRight
    21 -> ManeuverIcon.RAMP         // kExitLeft
    22 -> ManeuverIcon.STRAIGHT     // kStayStraight
    23 -> ManeuverIcon.SLIGHT_RIGHT // kStayRight
    24 -> ManeuverIcon.SLIGHT_LEFT  // kStayLeft
    25 -> ManeuverIcon.MERGE        // kMerge
    26 -> ManeuverIcon.ROUNDABOUT   // kRoundaboutEnter
    27 -> ManeuverIcon.ROUNDABOUT   // kRoundaboutExit
    else -> ManeuverIcon.UNKNOWN
}
