package com.itsdonebro.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores aggregated daily statistics.
 * One row per calendar day (keyed by "yyyy-MM-dd").
 */
@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey
    val date: String,                      // "2026-09-16"
    val reelsWatched: Int = 0,
    val totalWatchTimeSeconds: Long = 0L,
    val limitSeconds: Long = 30 * 60L,     // mirrors the setting at save-time
    val limitReached: Boolean = false,
    val limitReachedCount: Int = 0         // how many times user triggered the blocker
)
