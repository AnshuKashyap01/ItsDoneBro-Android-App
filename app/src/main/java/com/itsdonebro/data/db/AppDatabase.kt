package com.itsdonebro.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyStats::class, ReelSession::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun reelSessionDao(): ReelSessionDao
}
