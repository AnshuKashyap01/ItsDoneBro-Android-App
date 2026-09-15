package com.itsdonebro.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelSessionDao {

    @Insert
    suspend fun insert(session: ReelSession): Long

    @Query("SELECT * FROM reel_sessions WHERE date = :date ORDER BY startTime ASC")
    suspend fun getSessionsForDate(date: String): List<ReelSession>

    @Query("SELECT COUNT(*) FROM reel_sessions WHERE date = :date")
    fun observeCountForDate(date: String): Flow<Int>

    @Query("SELECT SUM(durationSeconds) FROM reel_sessions WHERE date = :date")
    suspend fun getTotalDurationForDate(date: String): Long?

    /** Purge sessions older than 90 days. */
    @Query("DELETE FROM reel_sessions WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
