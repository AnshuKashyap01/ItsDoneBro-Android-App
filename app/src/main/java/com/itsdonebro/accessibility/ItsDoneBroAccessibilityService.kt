package com.itsdonebro.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.itsdonebro.domain.ReelDetector
import com.itsdonebro.domain.TrackingEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The heart of ItsDoneBro's detection.
 *
 * Receives Android accessibility events scoped to com.instagram.android
 * (as declared in res/xml/accessibility_service_config.xml) and feeds them
 * into [TrackingEngine].
 *
 * Privacy note: we only inspect the *structure* of the UI tree (view IDs,
 * class names, content descriptions). We never capture screen text, images,
 * messages, or any personal content.
 */
@AndroidEntryPoint
class ItsDoneBroAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var trackingEngine: TrackingEngine

    private val instagramPackage = "com.instagram.android"
    private var isInstagramForeground = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val pkg = event.packageName?.toString() ?: return

        // ── Foreground app change ─────────────────────────────────────────────
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val nowInstagram = pkg == instagramPackage
            if (nowInstagram != isInstagramForeground) {
                isInstagramForeground = nowInstagram
                trackingEngine.onInstagramForeground(nowInstagram)
            }
            if (!isInstagramForeground) return
        }

        // ── Reel detection within Instagram ──────────────────────────────────
        if (!isInstagramForeground) return

        val rootNode = rootInActiveWindow ?: return
        val reelState = ReelDetector.detect(rootNode)
        trackingEngine.onReelStateDetected(reelState)
    }

    override fun onInterrupt() {
        // Accessibility service interrupted (e.g. phone call)
        // TrackingEngine will stop via the foreground service lifecycle
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service is active — nothing extra needed; config is in XML
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingEngine.onInstagramForeground(false)
    }
}
