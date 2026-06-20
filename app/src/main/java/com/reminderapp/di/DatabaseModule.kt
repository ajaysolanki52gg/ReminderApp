package com.reminderapp.di

import android.content.Context
import androidx.room.Room
import com.reminderapp.data.local.ReminderDatabase
import com.reminderapp.data.local.dao.ReminderDao
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.data.repository.ReminderRepositoryImpl
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
    fun provideDatabase(@ApplicationContext context: Context): ReminderDatabase =
        Room.databaseBuilder(
            context,
            ReminderDatabase::class.java,
            ReminderDatabase.DATABASE_NAME
        ).build()

    @Provides
    @Singleton
    fun provideReminderDao(db: ReminderDatabase): ReminderDao = db.reminderDao()

    @Provides
    @Singleton
    fun provideReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository = impl
}
