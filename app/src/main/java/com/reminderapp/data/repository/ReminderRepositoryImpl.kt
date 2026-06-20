package com.reminderapp.data.repository

import com.reminderapp.data.local.dao.ReminderDao
import com.reminderapp.data.local.entity.toDomain
import com.reminderapp.data.local.entity.toEntity
import com.reminderapp.domain.model.Reminder
import com.reminderapp.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao
) : ReminderRepository {

    override fun getUpcomingReminders(now: LocalDateTime, limit: Int): Flow<List<Reminder>> =
        dao.getUpcoming(now, limit).map { list -> list.map { it.toDomain() } }

    override fun getAllUpcomingReminders(now: LocalDateTime): Flow<List<Reminder>> =
        dao.getAllUpcoming(now).map { list -> list.map { it.toDomain() } }

    override fun getCompletedReminders(): Flow<List<Reminder>> =
        dao.getCompleted().map { list -> list.map { it.toDomain() } }

    override fun getMissedReminders(): Flow<List<Reminder>> =
        dao.getMissed().map { list -> list.map { it.toDomain() } }

    override fun getCompletedCount(): Flow<Int> = dao.getCompletedCount()

    override fun getMissedCount(): Flow<Int> = dao.getMissedCount()

    override suspend fun getReminderById(id: Long): Reminder? =
        dao.getById(id)?.toDomain()

    override fun getReminderByIdFlow(id: Long): Flow<Reminder?> =
        dao.getByIdFlow(id).map { it?.toDomain() }

    override suspend fun insertReminder(reminder: Reminder): Long =
        dao.insert(reminder.toEntity())

    override suspend fun updateReminder(reminder: Reminder) =
        dao.update(reminder.toEntity())

    override suspend fun deleteReminder(id: Long) =
        dao.deleteById(id)

    override suspend fun updateStatus(id: Long, status: ReminderStatus) =
        dao.updateStatus(id, status)

    override suspend fun getOverdueReminders(now: LocalDateTime): List<Reminder> =
        dao.getOverdueReminders(now).map { it.toDomain() }

    override suspend fun getAllActiveReminders(): List<Reminder> =
        dao.getAllActiveReminders().map { it.toDomain() }

    override suspend fun getAllReminders(): List<Reminder> =
        dao.getAllReminders().map { it.toDomain() }
}
