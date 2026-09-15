package com.itsdonebro.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsdonebro.data.db.DailyStats
import com.itsdonebro.data.db.DailyStatsDao
import com.itsdonebro.data.preferences.SettingsDataStore
import com.itsdonebro.domain.TrackingEngine
import com.itsdonebro.domain.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardUiState(
    val trackingState: TrackingState = TrackingState(),
    val weeklyStats: List<DailyStats> = emptyList(),
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val trackingEngine: TrackingEngine,
    private val dailyStatsDao: DailyStatsDao,
    private val settings: SettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        trackingEngine.state,
        dailyStatsDao.observeLastSevenDays()
    ) { tracking, weekly ->
        DashboardUiState(
            trackingState = tracking,
            weeklyStats   = weekly
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    /** Called from the UI after checking system permissions. */
    fun updatePermissionState(accessibility: Boolean, overlay: Boolean) {
        // In a full impl this would update a StateFlow; kept simple here.
    }
}
