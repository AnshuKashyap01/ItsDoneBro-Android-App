package com.itsdonebro.domain

import com.itsdonebro.data.db.DailyStats
import com.itsdonebro.data.db.DailyStatsDao
import com.itsdonebro.data.db.ReelSession
import com.itsdonebro.data.db.ReelSessionDao
import com.itsdonebro.data.preferences.SettingsDataStore
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
    object Warning80    : LimitEvent()          // 80% of limit used
    object Warning90    : LimitEvent()          // 90%
    object LimitReached : LimitEvent()
    data class RepeatedAttempt(val attempt: Int) : LimitEvent()
}

/**
 * Central tracking engine.
 *
 * Receives [ReelDetectionEvent]s from [ReelDetector] (via the AccessibilityService),
 * maintains live [TrackingState], persists sessions to Room, and emits [LimitEvent]s
 * when thresholds are crossed.
 *
 * Counting rule (from design doc):
 *   - Increment reel COUNT on ReelStarted — so a reel the user is mid-watching
 *     when the limit fires still appears in today's count.
 *   - Add watch TIME on ReelEnded — once the real duration is known.
 *   - Minimum duration gate: sessions < 100 ms are noise and not counted.
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

    /** The 3-layer detector — one instance for the lifetime of the engine. */
    val reelDetector = ReelDetector(
        scope = engineScope,
        onEvent = { event -> engineScope.launch { handleDetectionEvent(event) } }
    )

    // Warn-threshold tracking (so we only emit once per threshold crossing)
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

    /** Called when Instagram enters or leaves the foreground. */
    fun onInstagramForeground(isForeground: Boolean) {
        engineScope.launch {
            _state.update { it.copy(isInstagramForeground = isForeground) }
            if (!isForeground) {
                // Tell ReelDetector so it cleanly closes any open session
                reelDetector.onInstagramBackgrounded()
                _state.update {
                    it.copy(reelState = ReelState.UNKNOWN, isTracking = false)
                }
            }
        }
    }

    /**
     * Called on every accessibility event from Instagram.
     * Delegates directly to [ReelDetector.onTick] — the detector handles
     * debouncing, fingerprinting, and state transitions internally.
     */
    fun onAccessibilityTick(root: android.view.accessibility.AccessibilityNodeInfo) {
        // ReelDetector is coroutine-safe; it manages its own debounce job internally.
        reelDetector.onTick(root)
    }

    // ─── Detection event handler ──────────────────────────────────────────────

    private suspend fun handleDetectionEvent(event: ReelDetectionEvent) {
        when (event) {
            is ReelDetectionEvent.ReelStarted -> {
                // Count the reel NOW (even if the user is still watching)
                _state.update {
                    it.copy(
                        reelsToday = it.reelsToday + 1,
                        reelState = ReelState.REEL,
                        isTracking = true
                    )
                }
                updateMessage()
                persistDailyStats()
                checkThresholds()
            }

            is ReelDetectionEvent.ReelEnded -> {
                val durationSec = event.durationMs / 1000L
                if (event.durationMs >= MIN_COUNTABLE_DURATION_MS && durationSec >= 1L) {
                    // Persist the session record
                    val now = System.currentTimeMillis()
                    val start = now - event.durationMs
                    reelSessionDao.insert(
                        ReelSession(
                            date = todayString(),
                            startTime = start,
                            endTime = now,
                            durationSeconds = durationSec
                        )
                    )
                    // Accumulate watch time
                    _state.update {
                        it.copy(watchTimeTodaySeconds = it.watchTimeTodaySeconds + durationSec)
                    }
                    recomputeProgress()
                    persistDailyStats()
                    checkThresholds()
                }
            }

            is ReelDetectionEvent.TrackingPaused -> {
                // User navigated away from Reels within Instagram (e.g. opened DMs)
                _state.update { it.copy(reelState = ReelState.NOT_REEL, isTracking = false) }
            }

            is ReelDetectionEvent.TrackingStopped -> {
                // Instagram left the foreground — already handled in onInstagramForeground
                _state.update { it.copy(reelState = ReelState.UNKNOWN, isTracking = false) }
            }
        }
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
        val stats = dailyStatsDao.getByDate(today) ?: return
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
        // Restore warning flags so we don't re-trigger them on the same day
        val prog = _state.value.limitProgress
        if (prog >= 0.8f) warned80 = true
        if (prog >= 0.9f) warned90 = true
    }

    private suspend fun persistDailyStats() {
        val s = _state.value
        dailyStatsDao.upsert(
            DailyStats(
                date = todayString(),
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

    companion object {
        /** Sub-100ms sessions are noise — filter them out. */
        private const val MIN_COUNTABLE_DURATION_MS = 100L
    }
}
