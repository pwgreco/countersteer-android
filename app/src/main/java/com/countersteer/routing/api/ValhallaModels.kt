package com.countersteer.routing.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Request models ---

@Serializable
data class ValhallaRouteRequest(
    val locations: List<ValhallaLocation>,
    val costing: String = "motorcycle",
    @SerialName("costing_options") val costingOptions: ValhallaMotorcycleOptions? = null,
    @SerialName("directions_options") val directionsOptions: ValhallaDirectionsOptions = ValhallaDirectionsOptions(),
    val units: String = "kilometers",
    val alternates: Int? = null,
)

@Serializable
data class ValhallaLocation(
    val lat: Double,
    val lon: Double,
    val type: String? = null,
)

@Serializable
data class ValhallaMotorcycleOptions(
    val motorcycle: MotorcycleCostingOptions? = null,
)

@Serializable
data class MotorcycleCostingOptions(
    @SerialName("use_highways") val useHighways: Double? = null,
    @SerialName("use_trails") val useTrails: Double? = null,
    @SerialName("use_tolls") val useTolls: Double? = null,
    @SerialName("highway_factor") val highwayFactor: Double? = null,
)

@Serializable
data class ValhallaDirectionsOptions(
    val units: String = "kilometers",
    val language: String = "en-US",
)

// --- Response models ---

@Serializable
data class ValhallaRouteResponse(
    val trip: ValhallaTrip? = null,
    val alternates: List<ValhallaAlternate>? = null,
)

@Serializable
data class ValhallaAlternate(
    val trip: ValhallaTrip? = null,
)

@Serializable
data class ValhallaTrip(
    val legs: List<ValhallaLeg> = emptyList(),
    val summary: ValhallaSummary? = null,
    val status: Int? = null,
    @SerialName("status_message") val statusMessage: String? = null,
    val units: String? = null,
)

@Serializable
data class ValhallaLeg(
    val maneuvers: List<ValhallaManeuver> = emptyList(),
    val shape: String? = null,
    val summary: ValhallaSummary? = null,
)

@Serializable
data class ValhallaSummary(
    val length: Double = 0.0,
    val time: Double = 0.0,
    @SerialName("min_lat") val minLat: Double = 0.0,
    @SerialName("min_lon") val minLon: Double = 0.0,
    @SerialName("max_lat") val maxLat: Double = 0.0,
    @SerialName("max_lon") val maxLon: Double = 0.0,
)

@Serializable
data class ValhallaManeuver(
    val instruction: String = "",
    val type: Int = 0,
    @SerialName("street_names") val streetNames: List<String> = emptyList(),
    val length: Double = 0.0,
    val time: Double = 0.0,
    @SerialName("begin_shape_index") val beginShapeIndex: Int = 0,
    @SerialName("end_shape_index") val endShapeIndex: Int = 0,
    @SerialName("travel_mode") val travelMode: String = "",
    @SerialName("travel_type") val travelType: String = "",
    @SerialName("rough_surface") val roughSurface: Boolean? = null,
    @SerialName("speed_limit") val speedLimit: Int? = null,
)
