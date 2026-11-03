package com.countersteer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countersteer.data.dao.RideDao
import com.countersteer.data.entity.RideEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class PeriodStats(
    val totalRides: Int,
    val totalDistanceKm: Double,
    val totalDurationSeconds: Long,
    val avgDistanceKm: Double,
    val avgSpeedKmh: Double,
    val topSpeedKmh: Double,
    val totalElevationGainM: Double,
    val avgCurvatureScore: Double,
)

data class RideStatsState(
    val weekStats: PeriodStats? = null,
    val monthStats: PeriodStats? = null,
    val yearStats: PeriodStats? = null,
    val allTimeStats: PeriodStats? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class RideStatsViewModel @Inject constructor(
    rideDao: RideDao,
) : ViewModel() {

    val state: StateFlow<RideStatsState> = rideDao.getAll()
        .map { rides -> computeStats(rides) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RideStatsState())

    private fun computeStats(rides: List<RideEntity>): RideStatsState {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)

        val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart = now.withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val yearStart = now.withDayOfYear(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val weekRides = rides.filter { it.startedAt >= weekStart }
        val monthRides = rides.filter { it.startedAt >= monthStart }
        val yearRides = rides.filter { it.startedAt >= yearStart }

        return RideStatsState(
            weekStats = calculatePeriodStats(weekRides),
            monthStats = calculatePeriodStats(monthRides),
            yearStats = calculatePeriodStats(yearRides),
            allTimeStats = calculatePeriodStats(rides),
            isLoading = false,
        )
    }

    private fun calculatePeriodStats(rides: List<RideEntity>): PeriodStats? {
        if (rides.isEmpty()) return null

        val totalDuration = rides.sumOf { it.durationSeconds }
        val weightedSpeed = if (totalDuration > 0) {
            rides.sumOf { it.avgSpeedKmh * it.durationSeconds } / totalDuration
        } else {
            0.0
        }

        return PeriodStats(
            totalRides = rides.size,
            totalDistanceKm = rides.sumOf { it.distanceKm },
            totalDurationSeconds = totalDuration,
            avgDistanceKm = rides.sumOf { it.distanceKm } / rides.size,
            avgSpeedKmh = weightedSpeed,
            topSpeedKmh = rides.maxOf { it.maxSpeedKmh },
            totalElevationGainM = rides.sumOf { it.elevationGainM },
            avgCurvatureScore = rides.map { it.curvatureScore }.average(),
        )
    }
}
