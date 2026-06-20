package com.reminderapp.data.repository

import com.reminderapp.domain.model.Reminder
import com.reminderapp.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface ReminderRepository {
    fun getUpcomingReminders(now: LocalDateTime, limit: Int = 3): Flow<List<Reminder>>
    fun getAllUpcomingReminders(now: LocalDateTime): Flow<List<Reminder>>
    fun getCompletedReminders(): Flow<List<Reminder>>
    fun getMissedReminders(): Flow<List<Reminder>>
    fun getCompletedCount(): Flow<Int>
    fun getMissedCount(): Flow<Int>
    suspend fun getReminderById(id: Long): Reminder?
    fun getReminderByIdFlow(id: Long): Flow<Reminder?>
    suspend fun insertReminder(reminder: Reminder): Long
    suspend fun updateReminder(reminder: Reminder)
    suspend fun deleteReminder(id: Long)
    suspend fun updateStatus(id: Long, status: ReminderStatus)
    suspend fun getOverdueReminders(now: LocalDateTime): List<Reminder>
    suspend fun getAllActiveReminders(): List<Reminder>
    suspend fun getAllReminders(): List<Reminder>
}
