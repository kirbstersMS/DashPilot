package com.example.dashpilot.di

import android.content.Context
import com.example.dashpilot.data.GigDao
import com.example.dashpilot.data.GigDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGigDatabase(@ApplicationContext context: Context): GigDatabase {
        return GigDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideGigDao(database: GigDatabase): GigDao {
        return database.gigDao()
    }
}