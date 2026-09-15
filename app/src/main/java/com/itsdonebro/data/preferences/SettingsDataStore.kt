package com.itsdonebro.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Extension to create a single DataStore instance per process
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "itsdonebro_settings")

/** Blocking modes the user can choose from. */
enum class BlockingMode { HARD_BLOCK, WARNING_ONLY, COOLDOWN }

/** App personality / tone settings. */
enum class PersonalityMode { FRIENDLY_SARCASTIC, MOTIVATIONAL, MINIMAL }

/** Typed wrapper around DataStore<Preferences>. */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ─── Keys ────────────────────────────────────────────────────────────────
    private object Keys {
        val DAILY_LIMIT_MINUTES    = intPreferencesKey("daily_limit_minutes")
        val BLOCKING_MODE          = stringPreferencesKey("blocking_mode")
        val COOLDOWN_MINUTES       = intPreferencesKey("cooldown_minutes")
        val OVERLAY_ENABLED        = booleanPreferencesKey("overlay_enabled")
        val SHOW_COUNTER           = booleanPreferencesKey("show_counter")
        val SHOW_TIMER             = booleanPreferencesKey("show_timer")
        val PERSONALITY_MODE       = stringPreferencesKey("personality_mode")
        val NOTIF_SUMMARY          = booleanPreferencesKey("notif_summary")
        val NOTIF_WARNING          = booleanPreferencesKey("notif_warning")
        val ONBOARDING_COMPLETE    = booleanPreferencesKey("onboarding_complete")
        val TRACKING_ENABLED       = booleanPreferencesKey("tracking_enabled")
    }

    // ─── Defaults ────────────────────────────────────────────────────────────
    companion object {
        const val DEFAULT_DAILY_LIMIT_MINUTES = 30
        const val DEFAULT_COOLDOWN_MINUTES    = 30
    }

    // ─── Reads (Flow) ────────────────────────────────────────────────────────
    val dailyLimitMinutes: Flow<Int> = context.dataStore.data
        .catchIO().map { it[Keys.DAILY_LIMIT_MINUTES] ?: DEFAULT_DAILY_LIMIT_MINUTES }

    val blockingMode: Flow<BlockingMode> = context.dataStore.data
        .catchIO().map {
            BlockingMode.valueOf(it[Keys.BLOCKING_MODE] ?: BlockingMode.HARD_BLOCK.name)
        }

    val cooldownMinutes: Flow<Int> = context.dataStore.data
        .catchIO().map { it[Keys.COOLDOWN_MINUTES] ?: DEFAULT_COOLDOWN_MINUTES }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data
        .catchIO().map { it[Keys.OVERLAY_ENABLED] ?: true }

    val showCounter: Flow<Boolean> = context.dataStore.data
        .catchIO().map { it[Keys.SHOW_COUNTER] ?: true }

    val showTimer: Flow<Boolean> = context.dataStore.data
        .catchIO().map { it[Keys.SHOW_TIMER] ?: true }

    val personalityMode: Flow<PersonalityMode> = context.dataStore.data
        .catchIO().map {
            PersonalityMode.valueOf(
                it[Keys.PERSONALITY_MODE] ?: PersonalityMode.FRIENDLY_SARCASTIC.name
            )
        }

    val notifSummaryEnabled: Flow<Boolean> = context.dataStore.data
        .catchIO().map { it[Keys.NOTIF_SUMMARY] ?: true }

    val notifWarningEnabled: Flow<Boolean> = context.dataStore.data
        .catchIO().map { it[Keys.NOTIF_WARNING] ?: true }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .catchIO().map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val trackingEnabled: Flow<Boolean> = context.dataStore.data
        .catchIO().map { it[Keys.TRACKING_ENABLED] ?: true }

    // ─── Writes ──────────────────────────────────────────────────────────────
    suspend fun setDailyLimitMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.DAILY_LIMIT_MINUTES] = minutes }

    suspend fun setBlockingMode(mode: BlockingMode) =
        context.dataStore.edit { it[Keys.BLOCKING_MODE] = mode.name }

    suspend fun setCooldownMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.COOLDOWN_MINUTES] = minutes }

    suspend fun setOverlayEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.OVERLAY_ENABLED] = enabled }

    suspend fun setShowCounter(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_COUNTER] = show }

    suspend fun setShowTimer(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_TIMER] = show }

    suspend fun setPersonalityMode(mode: PersonalityMode) =
        context.dataStore.edit { it[Keys.PERSONALITY_MODE] = mode.name }

    suspend fun setNotifSummary(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIF_SUMMARY] = enabled }

    suspend fun setNotifWarning(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIF_WARNING] = enabled }

    suspend fun setOnboardingComplete(complete: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }

    suspend fun setTrackingEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.TRACKING_ENABLED] = enabled }
}

/** Silently swallows IO exceptions so the flow never crashes the app. */
private fun Flow<Preferences>.catchIO() =
    catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
