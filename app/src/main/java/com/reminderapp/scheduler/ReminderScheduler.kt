package com.reminderapp.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.reminderapp.domain.model.NotificationMode
import com.reminderapp.domain.model.Reminder
import com.reminderapp.domain.model.RecurrenceType
import com.reminderapp.notification.ReminderNotificationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager
) {
    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_REMINDER_DESCRIPTION = "reminder_description"
        const val EXTRA_NOTIFICATION_MODE = "notification_mode"
    }

    fun schedule(reminder: Reminder) {
        val triggerMillis = reminder.reminderDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val now = System.currentTimeMillis()
        if (triggerMillis <= now) return

        when (reminder.notificationMode) {
            NotificationMode.ALARM -> scheduleAlarm(reminder, triggerMillis)
            NotificationMode.NOTIFICATION -> scheduleWork(reminder, triggerMillis - now)
        }
    }

    fun cancel(reminderId: Long) {
        // Cancel alarm
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        alarmIntent?.let { alarmManager.cancel(it) }

        // Cancel work
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$reminderId")
    }

    fun rescheduleRecurring(reminder: Reminder) {
        if (reminder.recurrenceType == RecurrenceType.NONE) return
        val next = nextOccurrence(reminder) ?: return
        schedule(reminder.copy(reminderDateTime = next))
    }

    private fun scheduleAlarm(reminder: Reminder, triggerMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.description)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } catch (e: SecurityException) {
            // Fallback to inexact alarm if exact permission denied
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    private fun scheduleWork(reminder: Reminder, delayMillis: Long) {
        val data = workDataOf(
            EXTRA_REMINDER_ID to reminder.id,
            EXTRA_REMINDER_TITLE to reminder.title,
            EXTRA_REMINDER_DESCRIPTION to reminder.description
        )
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("reminder_${reminder.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder_${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun nextOccurrence(reminder: Reminder): LocalDateTime? {
        val base = reminder.reminderDateTime
        return when (reminder.recurrenceType) {
            RecurrenceType.DAILY -> base.plusDays(1)
            RecurrenceType.WEEKLY -> base.plusWeeks(1)
            RecurrenceType.MONTHLY -> base.plusMonths(1)
            RecurrenceType.YEARLY -> base.plusYears(1)
            RecurrenceType.NONE -> null
        }
    }
}
