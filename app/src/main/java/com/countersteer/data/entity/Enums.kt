package com.countersteer.data.entity

enum class RouteType {
    CURVY,
    SCENIC,
    QUICKEST,
}

enum class MarkerType {
    SPEED_TRAP,
    HAZARD,
    PHOTO_SPOT,
    CUSTOM,
    SPEED_CAMERA,
    RED_LIGHT_CAMERA,
}

enum class MarkerSource {
    USER,
    OSM,
}
