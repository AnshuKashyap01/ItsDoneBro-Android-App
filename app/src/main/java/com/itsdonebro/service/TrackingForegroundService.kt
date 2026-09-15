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
import com.itsdonebro.overlay.OverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

/**
 * Long-running foreground service that:
 *  1. Keeps [TrackingEngine] and [OverlayManager] alive even when the app is backgrounded.
 *  2. Shows / hides overlays based on TrackingState.
 *  3. Resets daily counters at midnight.
 *  4. Handles limit events (warnings, blocks).
 */
@AndroidEntryPoint
class TrackingForegroundService : Service() {

    @Inject lateinit var trackingEngine: TrackingEngine
    @Inject lateinit var overlayManager: OverlayManager
    @Inject lateinit var settings: SettingsDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID   = "itsdonebro_tracking"
        private const val NOTIF_ID     = 1001
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

    private fun observeTrackingState() {
        serviceScope.launch {
            trackingEngine.state.collect { state ->
                val overlayEnabled = settings.overlayEnabled.first()

                when {
                    // Limit reached → show full-screen blocker
                    state.isLimitReached && state.isInstagramForeground -> {
                        overlayManager.showBlockingOverlay(trackingEngine.state)
                    }
                    // Instagram active, tracking → show floating counter
                    state.isInstagramForeground && overlayEnabled -> {
                        overlayManager.hideBlockingOverlay()
                        overlayManager.showFloatingCounter(trackingEngine.state)
                    }
                    // Instagram closed → hide everything
                    !state.isInstagramForeground -> {
                        overlayManager.hideFloatingCounter()
                        overlayManager.hideBlockingOverlay()
                    }
                }
            }
        }
    }

    private fun observeLimitEvents() {
        serviceScope.launch {
            trackingEngine.limitEvents.collect { event ->
                when (event) {
                    is LimitEvent.Warning80 -> {
                        // Subtle notification — could vibrate lightly here
                    }
                    is LimitEvent.Warning90 -> {
                        // More urgent nudge
                    }
                    is LimitEvent.LimitReached -> {
                        // Overlay already handled via state; could vibrate
                        vibrate()
                    }
                    is LimitEvent.RepeatedAttempt -> {
                        // Each attempt escalates the blocking message (handled by overlay)
                    }
                }
            }
        }
    }

    // ─── Midnight reset ────────────────────────────────────────────────────────

    private fun scheduleMidnightReset() {
        serviceScope.launch {
            while (true) {
                val msUntilMidnight = millisUntilMidnight()
                delay(msUntilMidnight)
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
            .setSmallIcon(android.R.drawable.ic_media_play) // replace with proper icon
            .setContentTitle("ItsDoneBro is watching 👀")
            .setContentText("Counting your Reels so you don't have to.")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
