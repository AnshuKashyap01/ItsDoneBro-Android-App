package com.itsdonebro.di

import android.content.Context
import androidx.room.Room
import com.itsdonebro.data.db.AppDatabase
import com.itsdonebro.data.db.DailyStatsDao
import com.itsdonebro.data.db.ReelSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "itsdonebro.db"
        )
            .fallbackToDestructiveMigration()   // fine for MVP; add migrations later
            .build()

    @Provides
    @Singleton
    fun provideDailyStatsDao(db: AppDatabase): DailyStatsDao = db.dailyStatsDao()

    @Provides
    @Singleton
    fun provideReelSessionDao(db: AppDatabase): ReelSessionDao = db.reelSessionDao()
}
