package com.reminderapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.reminderapp.domain.model.*
import java.time.LocalDateTime

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["reminderDateTime"]),
        Index(value = ["status"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val reminderDateTime: LocalDateTime,
    val reminderType: ReminderType = ReminderType.ONE_TIME,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceValue: String = "",
    val notificationMode: NotificationMode = NotificationMode.NOTIFICATION,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

// ─── Mappers ──────────────────────────────────────────────────────────────────

fun ReminderEntity.toDomain() = com.reminderapp.domain.model.Reminder(
    id = id,
    title = title,
    description = description,
    reminderDateTime = reminderDateTime,
    reminderType = reminderType,
    recurrenceType = recurrenceType,
    recurrenceValue = recurrenceValue,
    notificationMode = notificationMode,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun com.reminderapp.domain.model.Reminder.toEntity() = ReminderEntity(
    id = id,
    title = title,
    description = description,
    reminderDateTime = reminderDateTime,
    reminderType = reminderType,
    recurrenceType = recurrenceType,
    recurrenceValue = recurrenceValue,
    notificationMode = notificationMode,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt
)
