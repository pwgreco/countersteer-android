package com.countersteer.data

import androidx.room.TypeConverter
import com.countersteer.data.entity.MarkerSource
import com.countersteer.data.entity.MarkerType
import com.countersteer.data.entity.RouteType

class Converters {

    @TypeConverter
    fun fromRouteType(value: RouteType): String = value.name.lowercase()

    @TypeConverter
    fun toRouteType(value: String): RouteType =
        RouteType.valueOf(value.uppercase())

    @TypeConverter
    fun fromMarkerType(value: MarkerType): String = value.name.lowercase()

    @TypeConverter
    fun toMarkerType(value: String): MarkerType =
        MarkerType.valueOf(value.uppercase())

    @TypeConverter
    fun fromMarkerSource(value: MarkerSource): String = value.name.lowercase()

    @TypeConverter
    fun toMarkerSource(value: String): MarkerSource =
        MarkerSource.valueOf(value.uppercase())
}
