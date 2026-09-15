package com.itsdonebro.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
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
 *
 * Overlay close-on-exit: we track Instagram's foreground state via TWO signals:
 *  1. TYPE_WINDOW_STATE_CHANGED — fires when a new window comes to the front.
 *  2. TYPE_WINDOWS_CHANGED      — fires when any window is removed (e.g. user
 *     swipes Instagram away). This catches cases where signal 1 is delayed or
 *     absent (e.g. quick swipe-to-home, recent-apps dismiss).
 */
@AndroidEntryPoint
class ItsDoneBroAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var trackingEngine: TrackingEngine

    private val instagramPackage = "com.instagram.android"
    private var isInstagramForeground = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {

            // ── Signal 1: Another app/window moved to the front ───────────────
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                val nowInstagram = pkg == instagramPackage
                if (nowInstagram != isInstagramForeground) {
                    isInstagramForeground = nowInstagram
                    trackingEngine.onInstagramForeground(nowInstagram)
                }
                if (!isInstagramForeground) return
                detectReels()
            }

            // ── Signal 2: Window list changed — check if Instagram is gone ────
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val instagramVisible = windows.any { window ->
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                            isInstagramWindow(window)
                }
                if (!instagramVisible && isInstagramForeground) {
                    isInstagramForeground = false
                    trackingEngine.onInstagramForeground(false)
                }
            }

            // ── Signal 3: Content changed inside Instagram ────────────────────
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!isInstagramForeground) return
                val pkg = event.packageName?.toString() ?: return
                if (pkg != instagramPackage) return
                detectReels()
            }

            else -> { /* ignore all other event types */ }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Run Reel detection against the current window root. */
    private fun detectReels() {
        val rootNode = rootInActiveWindow ?: return
        val reelState = ReelDetector.detect(rootNode)
        trackingEngine.onReelStateDetected(reelState)
    }

    /**
     * Check whether an [AccessibilityWindowInfo] belongs to Instagram.
     * Falls back to root-node package check when window title is unavailable.
     */
    private fun isInstagramWindow(window: AccessibilityWindowInfo): Boolean {
        return try {
            val root = window.root ?: return false
            val pkg = root.packageName?.toString()
            root.recycle()
            pkg == instagramPackage
        } catch (_: Exception) {
            false
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted (e.g. phone call).
        // Signal Instagram is gone so overlays close immediately.
        if (isInstagramForeground) {
            isInstagramForeground = false
            trackingEngine.onInstagramForeground(false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service is active — nothing extra needed; config is in XML
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isInstagramForeground) {
            isInstagramForeground = false
            trackingEngine.onInstagramForeground(false)
        }
    }
}
