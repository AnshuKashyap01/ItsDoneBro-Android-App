package com.itsdonebro.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsdonebro.data.preferences.BlockingMode
import com.itsdonebro.data.preferences.PersonalityMode
import com.itsdonebro.data.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val dailyLimitMinutes: Int = 30,
    val blockingMode: BlockingMode = BlockingMode.HARD_BLOCK,
    val cooldownMinutes: Int = 30,
    val overlayEnabled: Boolean = true,
    val showCounter: Boolean = true,
    val showTimer: Boolean = true,
    val personalityMode: PersonalityMode = PersonalityMode.FRIENDLY_SARCASTIC,
    val notifSummary: Boolean = true,
    val notifWarning: Boolean = true,
    val trackingEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.dailyLimitMinutes,
        settings.blockingMode,
        settings.cooldownMinutes,
        settings.overlayEnabled,
        settings.showCounter,
        settings.showTimer,
        settings.personalityMode,
        settings.notifSummaryEnabled,
        settings.notifWarningEnabled,
        settings.trackingEnabled
    ) { values ->
        SettingsUiState(
            dailyLimitMinutes = values[0] as Int,
            blockingMode      = values[1] as BlockingMode,
            cooldownMinutes   = values[2] as Int,
            overlayEnabled    = values[3] as Boolean,
            showCounter       = values[4] as Boolean,
            showTimer         = values[5] as Boolean,
            personalityMode   = values[6] as PersonalityMode,
            notifSummary      = values[7] as Boolean,
            notifWarning      = values[8] as Boolean,
            trackingEnabled   = values[9] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDailyLimit(minutes: Int) = viewModelScope.launch {
        settings.setDailyLimitMinutes(minutes)
    }
    fun setBlockingMode(mode: BlockingMode) = viewModelScope.launch {
        settings.setBlockingMode(mode)
    }
    fun setCooldown(minutes: Int) = viewModelScope.launch {
        settings.setCooldownMinutes(minutes)
    }
    fun setOverlayEnabled(v: Boolean)  = viewModelScope.launch { settings.setOverlayEnabled(v) }
    fun setShowCounter(v: Boolean)     = viewModelScope.launch { settings.setShowCounter(v) }
    fun setShowTimer(v: Boolean)       = viewModelScope.launch { settings.setShowTimer(v) }
    fun setPersonality(m: PersonalityMode) = viewModelScope.launch { settings.setPersonalityMode(m) }
    fun setNotifSummary(v: Boolean)    = viewModelScope.launch { settings.setNotifSummary(v) }
    fun setNotifWarning(v: Boolean)    = viewModelScope.launch { settings.setNotifWarning(v) }
    fun setTrackingEnabled(v: Boolean) = viewModelScope.launch { settings.setTrackingEnabled(v) }
}

// Helper for combine with 10 flows (Kotlin stdlib only goes to 5)
private fun <T> combine(vararg flows: Flow<*>, transform: suspend (Array<*>) -> T): Flow<T> =
    combine(flows.toList()) { transform(it) }
