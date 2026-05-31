package com.rostrum.di

import android.content.Context
import androidx.room.Room
import com.rostrum.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库模块 - 提供 Room 数据库实例
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "omnimaster.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideBookmarkDao(database: AppDatabase) = database.bookmarkDao()

    @Provides
    fun provideFileHistoryDao(database: AppDatabase) = database.fileHistoryDao()

    @Provides
    fun provideSettingDao(database: AppDatabase) = database.settingDao()
}
