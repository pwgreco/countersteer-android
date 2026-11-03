package com.countersteer.ui.screens

import androidx.lifecycle.ViewModel
import com.countersteer.data.dao.RideDao
import com.countersteer.data.entity.RideEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    rideDao: RideDao,
) : ViewModel() {
    val rides: Flow<List<RideEntity>> = rideDao.getAll()
}
