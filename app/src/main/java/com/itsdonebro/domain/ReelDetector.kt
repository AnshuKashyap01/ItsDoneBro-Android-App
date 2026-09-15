package com.itsdonebro.domain

import android.view.accessibility.AccessibilityNodeInfo

/** Represents our confidence about whether the current Instagram screen is a Reel. */
enum class ReelState { UNKNOWN, NOT_REEL, REEL }

/**
 * Multi-signal Reel detector.
 *
 * Instagram does not expose a public API for Reel state, so we infer it from
 * the accessibility node tree. This class intentionally uses multiple independent
 * signals so that a single Instagram UI change doesn't break detection entirely.
 *
 * Strategy:
 *   Signal A — Known Reel view IDs (fragile, but high confidence when present)
 *   Signal B — Content description keywords ("reel", "video")
 *   Signal C — Full-screen video node presence
 *
 * Decision table:
 *   A alone         → REEL
 *   B + C           → REEL
 *   C alone         → UNKNOWN  (could be a Story or regular video)
 *   none            → NOT_REEL
 */
object ReelDetector {

    // ─── Signal A: Known view IDs ─────────────────────────────────────────────
    // These change with Instagram updates; add new ones as they're discovered.
    private val REEL_VIEW_IDS = setOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/reel_viewer_root",
        "com.instagram.android:id/unified_clips_viewer",
        "com.instagram.android:id/clips_tab",
        "com.instagram.android:id/reels_tray_container",
        "com.instagram.android:id/video_player_container"
    )

    // ─── Signal B: Content description keywords ───────────────────────────────
    private val REEL_KEYWORDS = setOf("reel", "reels", "clip", "clips")

    // ─── Signal C: Video node class names ─────────────────────────────────────
    private val VIDEO_CLASS_NAMES = setOf(
        "android.widget.VideoView",
        "com.instagram.ui.widget.VideoView",
        "androidx.media3.ui.PlayerView",
        "com.google.android.exoplayer2.ui.StyledPlayerView"
    )

    /**
     * Inspects [rootNode] and returns the current [ReelState].
     * The caller owns [rootNode] recycling.
     */
    fun detect(rootNode: AccessibilityNodeInfo?): ReelState {
        if (rootNode == null) return ReelState.UNKNOWN

        val signalA = hasReelViewId(rootNode)
        if (signalA) return ReelState.REEL

        val signalB = hasReelKeyword(rootNode)
        val signalC = hasVideoNode(rootNode)

        return when {
            signalB && signalC -> ReelState.REEL
            signalC            -> ReelState.UNKNOWN
            else               -> ReelState.NOT_REEL
        }
    }

    // ─── Signal implementations ────────────────────────────────────────────────

    private fun hasReelViewId(root: AccessibilityNodeInfo): Boolean {
        for (id in REEL_VIEW_IDS) {
            if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) return true
        }
        return false
    }

    private fun hasReelKeyword(root: AccessibilityNodeInfo): Boolean {
        return traverseNodes(root) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            REEL_KEYWORDS.any { kw -> desc.contains(kw) || text.contains(kw) }
        }
    }

    private fun hasVideoNode(root: AccessibilityNodeInfo): Boolean {
        return traverseNodes(root) { node ->
            VIDEO_CLASS_NAMES.any { cls -> node.className?.toString() == cls }
        }
    }

    /**
     * BFS over the accessibility tree; stops early when [predicate] returns true.
     * Caps at 200 nodes to prevent excessive traversal on complex layouts.
     */
    private fun traverseNodes(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 200) {
            val node = queue.removeFirst()
            visited++
            if (predicate(node)) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }
}
