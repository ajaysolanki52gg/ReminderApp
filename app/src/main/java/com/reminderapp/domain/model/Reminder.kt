package com.reminderapp.domain.model

import java.time.LocalDateTime

// ─── Enums ────────────────────────────────────────────────────────────────────

enum class ReminderType {
    ONE_TIME,
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    YEARLY
}

enum class RecurrenceType {
    NONE,
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    YEARLY
}

enum class NotificationMode {
    NOTIFICATION,
    ALARM
}

enum class ReminderStatus {
    ACTIVE,
    COMPLETED,
    MISSED
}

// ─── Domain Model ─────────────────────────────────────────────────────────────

data class Reminder(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val reminderDateTime: LocalDateTime,
    val reminderType: ReminderType = ReminderType.ONE_TIME,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceValue: String = "",   // e.g. "MONDAY", "5" (day of month), "JUNE_10"
    val notificationMode: NotificationMode = NotificationMode.NOTIFICATION,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

// ─── Parser Result ─────────────────────────────────────────────────────────────

data class ReminderParseResult(
    val title: String,
    val dateTime: LocalDateTime?,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceValue: String = "",
    val reminderType: ReminderType = ReminderType.ONE_TIME,
    val confidence: Float = 1.0f,
    val rawInput: String = ""
)
