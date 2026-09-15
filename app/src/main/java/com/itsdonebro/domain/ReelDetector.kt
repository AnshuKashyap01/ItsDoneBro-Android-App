package com.itsdonebro.domain

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * ================================================================================
 * REEL DETECTION & COUNTING ENGINE
 * ================================================================================
 *
 * THE PROBLEM
 * -----------
 * Instagram's Reels viewer is a vertically-scrolling recycled pager. This breaks
 * naive counting approaches:
 *
 *   - The same AccessibilityNodeInfo view IDs stay in the tree across reels —
 *     only their text/content changes. "A new node appeared" is not a reliable
 *     signal of "a new reel started."
 *   - A single swipe fires a BURST of accessibility events (scroll, content
 *     changed, focus changed) — counting per-event over-counts massively.
 *   - Swipe animations pass through transient intermediate states that look
 *     like content changes but aren't a real new reel.
 *
 * THE APPROACH (three layers)
 * ----------------------------
 *  1. ReelScreenClassifier    — is the current screen a Reel at all? (confidence
 *                               score across multiple weak signals, not one
 *                               brittle check, because Instagram's UI/IDs change)
 *  2. ReelFingerprintExtractor — turn the *content* of the current reel
 *                               (username + caption + audio track) into a hash.
 *                               This is what "identifies" a reel — not the event.
 *  3. ReelDetector            — a debounced state machine that converts noisy,
 *                               rapidly-changing fingerprints into clean
 *                               ReelStarted / ReelEnded events, exactly once
 *                               per reel actually viewed.
 * ================================================================================
 */

/** Represents our confidence about whether the current Instagram screen is a Reel. */
enum class ReelState { UNKNOWN, NOT_REEL, REEL }

// ------------------------------------------------------------------------------
// LAYER 1 — Screen classification
// ------------------------------------------------------------------------------

object ReelScreenClassifier {

    private data class Signal(val weight: Int, val test: (AccessibilityNodeInfo, Int) -> Boolean)

    // No single signal is load-bearing — Instagram renames/reshuffles resource
    // IDs across app versions, so we score several weak, independent signals.
    private val signals = listOf(
        Signal(weight = 3) { root, _ -> hasFullScreenVideoContainer(root) },
        Signal(weight = 3) { root, screenW -> hasActionRail(root, screenW) },
        Signal(weight = 2) { root, _ -> hasIdContaining(root, "clips") || hasIdContaining(root, "reel") },
        Signal(weight = 1) { root, _ -> hasCaptionBlock(root) },
        Signal(weight = 1) { root, _ -> hasAudioHint(root) }
    )

    private const val REEL_THRESHOLD = 5      // confirm REEL at/above this score
    private const val NOT_REEL_THRESHOLD = 2  // confirm NOT_REEL at/below this score
    // Anything in between is ambiguous → caller keeps the previous state
    // instead of flip-flopping on every event.

    fun classify(root: AccessibilityNodeInfo, previous: ReelState): ReelState {
        val screenWidth = boundsOf(root)?.width() ?: return previous
        val score = signals.sumOf { if (it.test(root, screenWidth)) it.weight else 0 }
        return when {
            score >= REEL_THRESHOLD    -> ReelState.REEL
            score <= NOT_REEL_THRESHOLD -> ReelState.NOT_REEL
            else                       -> previous  // ambiguous — hold previous state
        }
    }

    private fun hasFullScreenVideoContainer(root: AccessibilityNodeInfo): Boolean {
        val screenArea = boundsOf(root)?.let { it.width().toLong() * it.height().toLong() } ?: return false
        return findNode(root, maxDepth = 8) { node ->
            val b = boundsOf(node) ?: return@findNode false
            val area = b.width().toLong() * b.height().toLong()
            node.isVisibleToUser && (
                node.className?.contains("VideoView", ignoreCase = true) == true ||
                area >= (screenArea * 0.85)
            )
        } != null
    }

    private fun hasActionRail(root: AccessibilityNodeInfo, screenWidth: Int): Boolean {
        // 3+ clickable icon-like nodes stacked along the right ~25% of the screen
        // (like / comment / share / audio-disc icons).
        val rightEdgeIcons = collectNodes(root, maxDepth = 10) { node ->
            node.isClickable &&
                node.className?.contains("Image", ignoreCase = true) == true &&
                (boundsOf(node)?.left ?: 0) > screenWidth * 0.75
        }
        return rightEdgeIcons.size >= 3
    }

    private fun hasIdContaining(root: AccessibilityNodeInfo, needle: String): Boolean =
        findNode(root, maxDepth = 12) {
            it.viewIdResourceName?.contains(needle, ignoreCase = true) == true
        } != null

    private fun hasCaptionBlock(root: AccessibilityNodeInfo): Boolean =
        findNode(root, maxDepth = 10) {
            it.className?.contains("TextView", ignoreCase = true) == true && !it.text.isNullOrBlank()
        } != null

    private fun hasAudioHint(root: AccessibilityNodeInfo): Boolean =
        findNode(root, maxDepth = 10) {
            val d = it.contentDescription?.toString()?.lowercase() ?: return@findNode false
            d.contains("audio") || d.contains("original sound") || d.contains("music")
        } != null

    // ── bounded tree-walk helpers (depth-capped to protect the UI thread) ──────

    private fun boundsOf(node: AccessibilityNodeInfo): Rect? {
        val r = Rect()
        node.getBoundsInScreen(r)
        return if (r.width() > 0 && r.height() > 0) r else null
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        maxDepth: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        if (maxDepth <= 0) return null
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNode(child, maxDepth - 1, predicate)?.let { return it }
        }
        return null
    }

    private fun collectNodes(
        root: AccessibilityNodeInfo,
        maxDepth: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (predicate(node)) out.add(node)
            if (depth <= 0) return
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it, depth - 1) }
            }
        }
        walk(root, maxDepth)
        return out
    }
}

// ------------------------------------------------------------------------------
// LAYER 2 — Content fingerprinting (the "identity" of a reel)
// ------------------------------------------------------------------------------

data class ReelFingerprint(val hash: String, val debugSignature: String)

object ReelFingerprintExtractor {

    /**
     * Returns null if we can't extract ANY stable signal yet (e.g. the reel is
     * still loading text in). Callers should treat null as "not ready", not as
     * "not a reel" — retry on the next event rather than counting nothing.
     */
    fun extract(root: AccessibilityNodeInfo): ReelFingerprint? {
        val username = findLikelyUsername(root)
        val caption  = findLikelyCaption(root, excluding = username)
        val audio    = findLikelyAudioTrack(root)

        if (username.isNullOrBlank() && caption.isNullOrBlank() && audio.isNullOrBlank()) return null

        val raw = "${username?.take(40).orEmpty()}|${caption?.take(80).orEmpty()}|${audio?.take(60).orEmpty()}"
        return ReelFingerprint(hash = sha256(raw), debugSignature = raw)
    }

    private fun findLikelyUsername(root: AccessibilityNodeInfo): String? {
        val screenH = Rect().also { root.getBoundsInScreen(it) }.height()
        return textNodes(root, maxDepth = 10)
            .filter { (node, text) ->
                node.isClickable &&
                    text.length in 2..30 &&
                    !text.contains(" ") &&
                    (Rect().also { node.getBoundsInScreen(it) }.top < screenH * 0.5)
            }
            .maxByOrNull { it.first.let { n -> screenH - Rect().also { n.getBoundsInScreen(it) }.top } }
            ?.second
    }

    private fun findLikelyCaption(root: AccessibilityNodeInfo, excluding: String?): String? {
        return textNodes(root, maxDepth = 10)
            .map { it.second }
            .filter { it.length > 12 && it != excluding }
            .maxByOrNull { it.length }
    }

    private fun findLikelyAudioTrack(root: AccessibilityNodeInfo): String? {
        return textNodes(root, maxDepth = 10)
            .map { it.second }
            .firstOrNull { it.contains("original audio", true) || it.contains("· ") }
    }

    private fun textNodes(root: AccessibilityNodeInfo, maxDepth: Int): List<Pair<AccessibilityNodeInfo, String>> {
        val out = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            val t = (node.text ?: node.contentDescription)?.toString()?.trim()
            if (!t.isNullOrEmpty()) out.add(node to t)
            if (depth <= 0) return
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth - 1) }
        }
        walk(root, maxDepth)
        return out
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

// ------------------------------------------------------------------------------
// LAYER 3 — Debounced state machine (this is what actually "counts" reels)
// ------------------------------------------------------------------------------

sealed class ReelDetectionEvent {
    /** A new distinct reel has been confirmed on screen. Count it now. */
    data class ReelStarted(val fingerprint: String, val startedAtMs: Long) : ReelDetectionEvent()

    /** The reel is done — add its watch duration to the running total. */
    data class ReelEnded(val fingerprint: String, val durationMs: Long) : ReelDetectionEvent()

    /** Left the reel viewer but Instagram is still foreground (e.g. opened comments). */
    object TrackingPaused : ReelDetectionEvent()

    /** Instagram left the foreground entirely. */
    object TrackingStopped : ReelDetectionEvent()
}

/**
 * Stateful reel detector. One instance per [TrackingEngine] lifetime.
 *
 * @param scope       Coroutine scope (single-threaded) for debounce jobs.
 * @param onEvent     Callback invoked with clean, de-duplicated events.
 * @param debounceMs  How long a fingerprint must be stable before it is
 *                    confirmed as a new reel (default 300 ms).
 */
class ReelDetector(
    private val scope: CoroutineScope,
    private val onEvent: (ReelDetectionEvent) -> Unit,
    private val debounceMs: Long = 300L
) {
    private var lastConfirmedFingerprint: String? = null
    private var currentCandidateFingerprint: String? = null
    private var candidateFirstSeenAtMs: Long = 0L
    private var confirmedStartedAtMs: Long = 0L
    private var debounceJob: Job? = null
    private var reelScreenActive = false

    /** Current screen classification (held between ticks to avoid ambiguous flip-flops). */
    private var lastClassifiedState: ReelState = ReelState.UNKNOWN

    /**
     * Call on every throttled accessibility event while Instagram is foreground.
     * [root] is the current window root node. [ReelScreenClassifier] is called
     * here so the AccessibilityService only needs to pass the raw node.
     */
    fun onTick(root: AccessibilityNodeInfo) {
        val reelState = ReelScreenClassifier.classify(root, lastClassifiedState)
        lastClassifiedState = reelState
        when (reelState) {
            ReelState.REEL     -> onReelScreen(root)
            ReelState.NOT_REEL -> onLeftReelScreen()
            ReelState.UNKNOWN  -> { /* not enough signal yet — hold current state */ }
        }
    }

    /** Call when Instagram itself leaves the foreground or the service disconnects. */
    fun onInstagramBackgrounded() {
        onLeftReelScreen()
        lastClassifiedState = ReelState.UNKNOWN
        onEvent(ReelDetectionEvent.TrackingStopped)
    }

    private fun onReelScreen(root: AccessibilityNodeInfo) {
        reelScreenActive = true
        val fp = ReelFingerprintExtractor.extract(root) ?: return // still loading — wait for next tick

        if (fp.hash == currentCandidateFingerprint) {
            return // same candidate as last tick — let the debounce timer keep running
        }

        // Content changed since the last tick → restart the debounce window.
        currentCandidateFingerprint = fp.hash
        candidateFirstSeenAtMs = System.currentTimeMillis()
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            confirmCandidate(fp.hash)
        }
    }

    private fun confirmCandidate(fingerprint: String) {
        // Guard against re-confirming the same reel due to extractor flicker.
        if (fingerprint == lastConfirmedFingerprint) return

        closeCurrentSessionIfAny()

        // Back-date the start to when this content was FIRST observed, not when
        // the debounce timer fired — otherwise every reel's measured duration
        // is short by ~debounceMs.
        confirmedStartedAtMs = candidateFirstSeenAtMs
        lastConfirmedFingerprint = fingerprint
        onEvent(ReelDetectionEvent.ReelStarted(fingerprint, confirmedStartedAtMs))
    }

    private fun onLeftReelScreen() {
        if (!reelScreenActive) return
        reelScreenActive = false
        debounceJob?.cancel()
        closeCurrentSessionIfAny()
        currentCandidateFingerprint = null
        onEvent(ReelDetectionEvent.TrackingPaused)
    }

    private fun closeCurrentSessionIfAny() {
        val fp = lastConfirmedFingerprint ?: return
        val duration = System.currentTimeMillis() - confirmedStartedAtMs
        onEvent(ReelDetectionEvent.ReelEnded(fp, duration))
        lastConfirmedFingerprint = null
    }
}
