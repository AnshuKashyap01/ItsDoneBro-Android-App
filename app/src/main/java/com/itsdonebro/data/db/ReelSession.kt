package com.itsdonebro.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records a single continuous Reel-watching session.
 * A new session starts when a Reel is confirmed active,
 * and ends when the user swipes away, leaves Instagram, or the limit is hit.
 */
@Entity(tableName = "reel_sessions")
data class ReelSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,                   // "yyyy-MM-dd" — for fast daily queries
    val startTime: Long,                // epoch millis
    val endTime: Long,                  // epoch millis
    val durationSeconds: Long           // (endTime - startTime) / 1000
)
