package com.countersteer.routing.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TomTomFlowResponse(
    val flowSegmentData: TomTomFlowSegmentData? = null,
)

@Serializable
data class TomTomFlowSegmentData(
    val currentSpeed: Double? = null,
    val freeFlowSpeed: Double? = null,
    val currentTravelTime: Int? = null,
    val freeFlowTravelTime: Int? = null,
    @SerialName("confidence") val confidence: Double? = null,
)
