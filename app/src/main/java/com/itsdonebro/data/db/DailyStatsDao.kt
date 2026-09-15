package com.itsdonebro.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatsDao {

    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyStats?

    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyStats?>

    /** Returns the last 7 days, ordered newest first. */
    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT 7")
    fun observeLastSevenDays(): Flow<List<DailyStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: DailyStats)

    @Delete
    suspend fun delete(stats: DailyStats)

    /** Purge rows older than 90 days to keep the DB lean. */
    @Query("DELETE FROM daily_stats WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
