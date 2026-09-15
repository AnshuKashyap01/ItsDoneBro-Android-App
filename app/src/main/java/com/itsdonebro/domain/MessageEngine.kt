package com.itsdonebro.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects contextually appropriate messages based on usage progress.
 *
 * All copy matches the design doc's "Brand Personality & Voice" sections 30–49.
 * Rule: roast the scrolling, not the person.
 */
@Singleton
class MessageEngine @Inject constructor() {

    // ─── Message pools ────────────────────────────────────────────────────────

    private val lowUsage = listOf(
        "Warm-up round.",
        "Pretty reasonable so far.",
        "Okay, okay. I see you.",
        "Just getting started, huh?",
        "Casual scroll detected."
    )

    private val mediumUsage = listOf(
        "Still scrolling? Respectfully... why?",
        "Your thumb is doing overtime.",
        "Bro. We have plans.",
        "This Reel better be important.",
        "Instagram has successfully kidnapped another few minutes.",
        "Okay. We have officially entered goblin mode.",
        "Your thumb has been promoted to CEO."
    )

    private val nearLimit = listOf(
        "Two minutes left. Make them count.",
        "Your contract with Instagram is almost over.",
        "Final warning, soldier.",
        "This Reel better be Oscar-worthy.",
        "Make your final scroll a legendary one.",
        "This is cinema."
    )

    private val atLimit = listOf(
        "WELL WELL WELL... You said \"just 5 minutes.\"\nInstagram wins. Again.",
        "BRO. We need to talk.\n\"That next Reel\" is not coming to save us.",
        "Congratulations. I'm putting the Reels behind a wall.",
        "The council has reviewed your scrolling activity.\nThe council has decided... NO MORE.",
        "Your daily limit has been absolutely cooked."
    )

    private val repeatAttempts = listOf(
        "You already hit your limit.\nNice try though.",
        "My brother in scrolling...\nWe've been through this.",
        "You: \"I'll just check one.\"\nItsDoneBro: \"Absolutely not.\"",
        "At this point, you're negotiating with an app.\nGo outside.",
        "Respectfully...\nNO.",
        "Still here? Bold strategy.",
        "Your persistence is impressive and also concerning."
    )

    private val achievements = listOf(
        "Nice. You stopped before the limit.",
        "That's called self-control.",
        "Your future self just high-fived you.",
        "Bro actually stopped scrolling.",
        "Character development unlocked."
    )

    private val noUsage = listOf(
        "No Reels today.\nYour thumb is unemployed.\nGood for it.",
        "Zero Reels. Wow.\nPeaceful.\nSuspiciously peaceful.",
        "Today: 0 Reels.\nFuture you is impressed."
    )

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a contextual message string.
     *
     * @param progress      0.0 – 1.0+ (watchTime / dailyLimit)
     * @param reelCount     number of Reels watched today
     * @param attemptCount  number of times the user tried to bypass the block
     */
    fun getMessage(progress: Float, reelCount: Int, attemptCount: Int = 0): String {
        if (reelCount == 0) return noUsage.random()
        if (attemptCount > 0) return getRepeatAttemptMessage(attemptCount)
        return when {
            progress >= 1.0f  -> atLimit.random()
            progress >= 0.85f -> nearLimit.random()
            progress >= 0.40f -> mediumUsage.random()
            else              -> lowUsage.random()
        }
    }

    /** Returns escalating messages the more the user tries after the block. */
    fun getRepeatAttemptMessage(attempt: Int): String {
        val idx = (attempt - 1).coerceIn(0, repeatAttempts.lastIndex)
        return repeatAttempts[idx]
    }

    /** Returns a positive-reinforcement achievement message. */
    fun getAchievementMessage(): String = achievements.random()

    /** Returns the limit-reached main headline message. */
    fun getLimitReachedMessage(reelsWatched: Int, minutesSpent: Long): String {
        return atLimit.random() + "\n\n${reelsWatched} Reels · ${minutesSpent}m"
    }

    /** Returns a warning message for the given percentage threshold (80, 90, 95, 99). */
    fun getWarningMessage(progress: Float, remainingSeconds: Long): String {
        val mins  = remainingSeconds / 60
        val secs  = remainingSeconds % 60
        val timeStr = if (mins > 0) "${mins}m ${secs}s left" else "${secs}s left"
        return when {
            progress >= 0.99f -> "⚠️ ${secs}s.\nThis is cinema."
            progress >= 0.95f -> "⚠️ ${timeStr}.\nMake your final scroll a legendary one."
            progress >= 0.90f -> "⚠️ ${timeStr}.\nThis Reel better be Oscar-worthy."
            else              -> "⚠️ ${timeStr}.\nJust letting you know... future-you is watching."
        }
    }
}
