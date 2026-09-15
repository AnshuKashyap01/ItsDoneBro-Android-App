package com.itsdonebro.domain

import com.itsdonebro.data.db.DailyStats
import com.itsdonebro.data.db.DailyStatsDao
import com.itsdonebro.data.db.ReelSession
import com.itsdonebro.data.db.ReelSessionDao
import com.itsdonebro.data.preferences.SettingsDataStore
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// ─── Public state model ──────────────────────────────────────────────────────

data class TrackingState(
    val isInstagramForeground: Boolean = false,
    val reelState: ReelState = ReelState.UNKNOWN,
    val reelsToday: Int = 0,
    val watchTimeTodaySeconds: Long = 0L,
    val dailyLimitSeconds: Long = 30 * 60L,
    val isLimitReached: Boolean = false,
    val limitProgress: Float = 0f,             // 0.0 – 1.0+
    val blockAttempts: Int = 0,                // how many times user tried after block
    val currentMessage: String = "Warm-up round.",
    val isTracking: Boolean = false            // true while actively watching a Reel
)

// ─── Limit events emitted to OverlayManager / ForegroundService ──────────────

sealed class LimitEvent {
    object Warning80  : LimitEvent()           // 80% of limit used
    object Warning90  : LimitEvent()           // 90%
    object LimitReached : LimitEvent()
    data class RepeatedAttempt(val attempt: Int) : LimitEvent()
}

/**
 * Central tracking engine.
 *
 * Receives [ReelState] events from the AccessibilityService,
 * maintains live [TrackingState], persists sessions to Room,
 * and emits [LimitEvent]s when thresholds are crossed.
 *
 * Thread-safety: all mutations happen on [engineScope] (single-threaded dispatcher).
 */
@Singleton
class TrackingEngine @Inject constructor(
    private val dailyStatsDao: DailyStatsDao,
    private val reelSessionDao: ReelSessionDao,
    private val settings: SettingsDataStore,
    private val messageEngine: MessageEngine
) {
    private val engineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(1)
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _limitEvents = MutableSharedFlow<LimitEvent>(extraBufferCapacity = 8)
    val limitEvents: SharedFlow<LimitEvent> = _limitEvents.asSharedFlow()

    // Session tracking
    private var sessionStart: Long? = null
    private var lastConfirmedReelState: ReelState = ReelState.UNKNOWN
    private var debounceJob: Job? = null

    // Warn-threshold tracking (so we only emit once per threshold)
    private var warned80 = false
    private var warned90 = false

    init {
        // Keep daily limit in sync with DataStore settings
        engineScope.launch {
            settings.dailyLimitMinutes.collect { minutes ->
                val limitSec = minutes * 60L
                _state.update { it.copy(dailyLimitSeconds = limitSec) }
                recomputeProgress()
            }
        }
        // Load today's persisted stats on start
        engineScope.launch { loadTodayStats() }
    }

    // ─── Called from AccessibilityService ────────────────────────────────────

    fun onInstagramForeground(isForeground: Boolean) {
        engineScope.launch {
            _state.update { it.copy(isInstagramForeground = isForeground) }
            if (!isForeground) {
                // Instagram backgrounded — close any open session
                closeCurrentSession()
                _state.update { it.copy(reelState = ReelState.UNKNOWN, isTracking = false) }
            }
        }
    }

    /**
     * Called on every accessibility event from Instagram.
     * Uses a 500 ms debounce to avoid counting intermediate scroll states.
     */
    fun onReelStateDetected(detected: ReelState) {
        debounceJob?.cancel()
        debounceJob = engineScope.launch {
            delay(500L)
            processReelState(detected)
        }
    }

    // ─── State machine ────────────────────────────────────────────────────────

    private suspend fun processReelState(newState: ReelState) {
        val current = _state.value
        if (!current.isInstagramForeground) return

        // Ignore UNKNOWN — wait for a confident signal
        if (newState == ReelState.UNKNOWN) return

        // Transition: was NOT tracking, now detecting a Reel → start session
        if (newState == ReelState.REEL && lastConfirmedReelState != ReelState.REEL) {
            startNewReelSession()
        }

        // Transition: was tracking a Reel, moved to non-Reel → close session + count
        if (newState != ReelState.REEL && lastConfirmedReelState == ReelState.REEL) {
            closeCurrentSession()
            incrementReelCount()
        }

        // Reel→Reel (new Reel scrolled into view while already tracking) — count and restart
        if (newState == ReelState.REEL && lastConfirmedReelState == ReelState.REEL) {
            val elapsed = sessionStart?.let { (System.currentTimeMillis() - it) / 1000L } ?: 0L
            if (elapsed > 2L) {          // at least 2 sec on previous reel before counting
                closeCurrentSession()
                incrementReelCount()
                startNewReelSession()
            }
        }

        lastConfirmedReelState = newState
        _state.update { it.copy(reelState = newState, isTracking = newState == ReelState.REEL) }
    }

    // ─── Session management ───────────────────────────────────────────────────

    private fun startNewReelSession() {
        sessionStart = System.currentTimeMillis()
    }

    private suspend fun closeCurrentSession() {
        val start = sessionStart ?: return
        sessionStart = null
        val end = System.currentTimeMillis()
        val duration = (end - start) / 1000L
        if (duration < 1L) return          // ignore sub-second blips

        val today = todayString()
        reelSessionDao.insert(
            ReelSession(date = today, startTime = start, endTime = end, durationSeconds = duration)
        )

        // Accumulate watch time
        _state.update { it.copy(watchTimeTodaySeconds = it.watchTimeTodaySeconds + duration) }
        recomputeProgress()
        persistDailyStats()
        checkThresholds()
    }

    private suspend fun incrementReelCount() {
        _state.update { it.copy(reelsToday = it.reelsToday + 1) }
        updateMessage()
        persistDailyStats()
    }

    // ─── Limit / threshold logic ──────────────────────────────────────────────

    private suspend fun checkThresholds() {
        val progress = _state.value.limitProgress
        val wasLimitReached = _state.value.isLimitReached

        if (!warned80 && progress >= 0.8f) {
            warned80 = true
            _limitEvents.emit(LimitEvent.Warning80)
        }
        if (!warned90 && progress >= 0.9f) {
            warned90 = true
            _limitEvents.emit(LimitEvent.Warning90)
        }
        if (!wasLimitReached && progress >= 1.0f) {
            _state.update { it.copy(isLimitReached = true) }
            _limitEvents.emit(LimitEvent.LimitReached)
            persistDailyStats()
        }
    }

    /** Called by OverlayManager when the user tries to re-open Reels after the block. */
    fun onBlockedAttempt() {
        engineScope.launch {
            val attempts = _state.value.blockAttempts + 1
            _state.update { it.copy(blockAttempts = attempts) }
            _limitEvents.emit(LimitEvent.RepeatedAttempt(attempts))
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun recomputeProgress() {
        val s = _state.value
        val progress = if (s.dailyLimitSeconds > 0)
            (s.watchTimeTodaySeconds.toFloat() / s.dailyLimitSeconds).coerceAtLeast(0f)
        else 0f
        _state.update { it.copy(limitProgress = progress) }
    }

    private fun updateMessage() {
        val s = _state.value
        val msg = messageEngine.getMessage(s.limitProgress, s.reelsToday, s.blockAttempts)
        _state.update { it.copy(currentMessage = msg) }
    }

    private suspend fun loadTodayStats() {
        val today = todayString()
        val stats = dailyStatsDao.getByDate(today)
        if (stats != null) {
            val limitSec = settings.dailyLimitMinutes.first() * 60L
            _state.update {
                it.copy(
                    reelsToday = stats.reelsWatched,
                    watchTimeTodaySeconds = stats.totalWatchTimeSeconds,
                    dailyLimitSeconds = limitSec,
                    isLimitReached = stats.limitReached,
                    limitProgress = if (limitSec > 0)
                        stats.totalWatchTimeSeconds.toFloat() / limitSec else 0f
                )
            }
            // Restore warning flags
            val prog = _state.value.limitProgress
            if (prog >= 0.8f) warned80 = true
            if (prog >= 0.9f) warned90 = true
        }
    }

    private suspend fun persistDailyStats() {
        val s = _state.value
        val today = todayString()
        dailyStatsDao.upsert(
            DailyStats(
                date = today,
                reelsWatched = s.reelsToday,
                totalWatchTimeSeconds = s.watchTimeTodaySeconds,
                limitSeconds = s.dailyLimitSeconds,
                limitReached = s.isLimitReached,
                limitReachedCount = s.blockAttempts
            )
        )
    }

    private fun todayString(): String = dateFormat.format(Date())

    /** Resets daily counters at midnight — called by the foreground service. */
    fun resetDailyCounters() {
        engineScope.launch {
            _state.update {
                it.copy(
                    reelsToday = 0,
                    watchTimeTodaySeconds = 0L,
                    isLimitReached = false,
                    limitProgress = 0f,
                    blockAttempts = 0,
                    currentMessage = "Warm-up round."
                )
            }
            warned80 = false
            warned90 = false
        }
    }

    fun destroy() {
        engineScope.cancel()
    }
}
