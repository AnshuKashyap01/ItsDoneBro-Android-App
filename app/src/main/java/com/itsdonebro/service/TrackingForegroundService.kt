package com.itsdonebro.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.itsdonebro.MainActivity
import com.itsdonebro.R
import com.itsdonebro.data.preferences.SettingsDataStore
import com.itsdonebro.domain.LimitEvent
import com.itsdonebro.domain.TrackingEngine
import com.itsdonebro.domain.TrackingState
import com.itsdonebro.overlay.OverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

/**
 * Long-running foreground service that:
 *  1. Keeps [TrackingEngine] and [OverlayManager] alive even when the app is backgrounded.
 *  2. Shows / hides overlays based on TrackingState changes.
 *  3. Resets daily counters at midnight.
 *  4. Handles limit events (warnings, blocks).
 *
 * Overlay lifecycle:
 *  - Instagram opens  → show floating counter immediately
 *  - Limit reached    → replace counter with full-screen blocking overlay
 *  - Instagram closes → hide ALL overlays immediately (hideAll)
 */
@AndroidEntryPoint
class TrackingForegroundService : Service() {

    @Inject lateinit var trackingEngine: TrackingEngine
    @Inject lateinit var overlayManager: OverlayManager
    @Inject lateinit var settings: SettingsDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID = "itsdonebro_tracking"
        private const val NOTIF_ID   = 1001
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        observeTrackingState()
        observeLimitEvents()
        scheduleMidnightReset()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY   // restart automatically if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        overlayManager.destroy()
        super.onDestroy()
    }

    // ─── State observation ─────────────────────────────────────────────────────

    /**
     * Observe [TrackingState] and drive overlay visibility.
     *
     * Rules (evaluated on every state emission):
     *  1. Instagram NOT foreground  → hide everything, immediately.
     *  2. Limit reached + Instagram → show blocking overlay (hides counter automatically).
     *  3. Instagram foreground, no limit → show floating counter.
     *
     * We combine overlayEnabled as a separate flow so the overlay reacts to
     * the setting changing at runtime without needing a suspend call inside collect.
     */
    private fun observeTrackingState() {
        serviceScope.launch {
            // Combine tracking state + overlay toggle into a single stream
            trackingEngine.state
                .combine(settings.overlayEnabled) { state, overlayEnabled ->
                    Pair(state, overlayEnabled)
                }
                .collect { (state, overlayEnabled) ->
                    applyOverlayState(state, overlayEnabled)
                }
        }
    }

    private fun applyOverlayState(state: TrackingState, overlayEnabled: Boolean) {
        when {
            // ── Instagram is NOT in foreground → hide everything right now ──────
            !state.isInstagramForeground -> {
                overlayManager.hideAll()
            }

            // ── Limit reached while Instagram is open → full-screen block ────────
            state.isLimitReached && state.isInstagramForeground -> {
                // showBlockingOverlay is idempotent (guarded by blockingView != null)
                overlayManager.showBlockingOverlay(trackingEngine.state)
            }

            // ── Instagram is open, no limit, overlay enabled → floating counter ──
            state.isInstagramForeground && overlayEnabled -> {
                // Ensure blocking overlay is gone (e.g. after daily reset)
                overlayManager.hideBlockingOverlay()
                // showFloatingCounter is idempotent (guarded by counterView != null)
                overlayManager.showFloatingCounter(trackingEngine.state)
            }

            // ── Instagram is open but overlay setting is off → hide counter ──────
            state.isInstagramForeground && !overlayEnabled -> {
                overlayManager.hideFloatingCounter()
            }
        }
    }

    private fun observeLimitEvents() {
        serviceScope.launch {
            trackingEngine.limitEvents.collect { event ->
                when (event) {
                    is LimitEvent.Warning80 -> {
                        // Subtle nudge — could vibrate lightly
                    }
                    is LimitEvent.Warning90 -> {
                        // More urgent nudge
                    }
                    is LimitEvent.LimitReached -> {
                        // Blocking overlay handled via state in applyOverlayState
                        vibrate()
                    }
                    is LimitEvent.RepeatedAttempt -> {
                        // Escalating message handled in BlockingOverlayContent
                    }
                }
            }
        }
    }

    // ─── Midnight reset ────────────────────────────────────────────────────────

    private fun scheduleMidnightReset() {
        serviceScope.launch {
            while (true) {
                delay(millisUntilMidnight())
                trackingEngine.resetDailyCounters()
            }
        }
    }

    private fun millisUntilMidnight(): Long {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        return (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L)
    }

    // ─── Vibration ─────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.vibrate(longArrayOf(0, 200, 100, 200), -1)
    }

    // ─── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reel Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ItsDoneBro runs in the background to track your Reels."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ItsDoneBro is watching 👀")
            .setContentText("Counting your Reels so you don't have to.")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
