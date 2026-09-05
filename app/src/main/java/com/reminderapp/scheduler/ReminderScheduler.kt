package com.reminderapp.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import com.reminderapp.MainActivity
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.NotificationMode
import com.reminderapp.domain.model.Reminder
import com.reminderapp.domain.model.RecurrenceType
import com.reminderapp.domain.model.ReminderStatus
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
    private val alarmManager: AlarmManager,
    private val repository: ReminderRepository
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

    suspend fun cancelAll() {
        val reminders = repository.getAllReminders()
        reminders.forEach { cancel(it.id) }
        // Also cancel any work tagged with "reminder" if we start using it, 
        // or just rely on the IDs. For safety:
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder")
    }

    suspend fun rescheduleRecurring(reminder: Reminder) {
        if (reminder.recurrenceType == RecurrenceType.NONE) return
        val next = nextOccurrence(reminder) ?: return
        // Persist the advanced date/status - without this, every future reschedule keeps reading
        // the ORIGINAL reminderDateTime back out of the DB, so the 2nd+ occurrence computes a
        // next-time that's already in the past and schedule() silently drops it (recurring
        // reminders would stop firing after their 2nd occurrence).
        val updated = reminder.copy(reminderDateTime = next, status = ReminderStatus.ACTIVE)
        repository.updateReminder(updated)
        schedule(updated)
    }

    /** Whether the app can currently schedule exact alarms (always true below API 31). */
    fun hasExactAlarmPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Re-fires the alarm/notification for [reminderId] after [minutes], used by the Snooze action. */
    suspend fun snooze(reminderId: Long, minutes: Int) {
        snooze(reminderId, LocalDateTime.now().plusMinutes(minutes.toLong()))
    }

    /** Re-fires the alarm/notification for [reminderId] at [newDateTime]. */
    suspend fun snooze(reminderId: Long, newDateTime: LocalDateTime) {
        val reminder = repository.getReminderById(reminderId) ?: return
        val updatedReminder = reminder.copy(
            reminderDateTime = newDateTime,
            status = ReminderStatus.ACTIVE
        )
        repository.updateReminder(updatedReminder)
        schedule(updatedReminder)
    }

    private fun scheduleAlarm(reminder: Reminder, triggerMillis: Long) =
        scheduleAlarmBroadcast(reminder.id, reminder.title, reminder.description, triggerMillis)

    private fun scheduleAlarmBroadcast(reminderId: Long, title: String, description: String, triggerMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_REMINDER_DESCRIPTION, description)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent fired if the user taps the alarm-clock icon in the status bar.
        val showIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (hasExactAlarmPermission()) {
                // setAlarmClock() is used instead of setExactAndAllowWhileIdle(): it is exempt
                // from Doze/App Standby *and* from aggressive OEM battery managers (e.g. OnePlus
                // OxygenOS "deep optimization"), which were silently delaying or dropping alarms
                // fired while the app was in the background.
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, showIntent), pendingIntent)
            } else {
                // Exact alarms were not granted (Android 12+ requires explicit user consent).
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
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
            .addTag("reminder")
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
