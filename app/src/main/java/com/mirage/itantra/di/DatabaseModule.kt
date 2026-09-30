package com.mirage.itantra.di

import android.content.Context
import androidx.room.Room
import com.mirage.itantra.data.local.ITantraDatabase
import com.mirage.itantra.data.local.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database and DAO instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ITantraDatabase = Room.databaseBuilder(
        context,
        ITantraDatabase::class.java,
        ITantraDatabase.DATABASE_NAME
    )
    .fallbackToDestructiveMigration()
    .build()

    @Provides
    fun provideMessageDao(database: ITantraDatabase): MessageDao =
        database.messageDao()
}
