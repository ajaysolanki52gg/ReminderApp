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
import com.reminderapp.domain.model.WeekDays
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
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
        val triggerMillis = reminder.nextTriggerTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val now = System.currentTimeMillis()
        if (triggerMillis <= now) return

        // Older builds delivered NOTIFICATION-mode reminders through WorkManager, which is not
        // exact (Doze can delay it by a long time). Drop any such leftover job so the reminder
        // doesn't fire twice now that both modes use exact AlarmManager alarms.
        WorkManager.getInstance(context).cancelUniqueWork("reminder_${reminder.id}")

        scheduleAlarmBroadcast(reminder, triggerMillis)
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
        // Completing/dismissing a *snoozed* occurrence: the regular series may already have been
        // advanced, so continue it rather than stepping forward again (which would skip a day).
        if (reminder.snoozedUntil != null) return resumeSeries(reminder)
        val next = nextOccurrence(reminder, LocalDateTime.now()) ?: return
        // Persist the advanced date/status - without this, every future reschedule keeps reading
        // the ORIGINAL reminderDateTime back out of the DB, so the 2nd+ occurrence computes a
        // next-time that's already in the past and schedule() silently drops it (recurring
        // reminders would stop firing after their 2nd occurrence).
        val updated = reminder.copy(reminderDateTime = next, status = ReminderStatus.ACTIVE, snoozedUntil = null)
        repository.updateReminder(updated)
        schedule(updated)
    }

    /**
     * A recurring reminder rang (normally or after a snooze) and has been handled. Drop any
     * snooze and continue the regular series: if the regular occurrence is still ahead (it was
     * already advanced elsewhere) just schedule it, otherwise advance from the regular time -
     * never from the snoozed time, so 8:00 snoozed to 8:10 still repeats at 8:00. Safe to call
     * more than once for the same ring without skipping an occurrence.
     */
    suspend fun resumeSeries(reminder: Reminder) {
        if (reminder.recurrenceType == RecurrenceType.NONE) return
        val cleared = reminder.copy(snoozedUntil = null, status = ReminderStatus.ACTIVE)
        if (cleared.reminderDateTime.isAfter(LocalDateTime.now())) {
            repository.updateReminder(cleared)
            schedule(cleared)
        } else {
            rescheduleRecurring(cleared)
        }
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
        val updatedReminder = if (reminder.recurrenceType == RecurrenceType.NONE) {
            // One-time: the snoozed time simply becomes the reminder's time.
            reminder.copy(reminderDateTime = newDateTime, snoozedUntil = null, status = ReminderStatus.ACTIVE)
        } else {
            // Recurring: keep the regular time (reminderDateTime) untouched and remember the
            // snooze separately, so the series doesn't drift by the snooze amount.
            reminder.copy(snoozedUntil = newDateTime, status = ReminderStatus.ACTIVE)
        }
        repository.updateReminder(updatedReminder)
        schedule(updatedReminder)
    }

    private fun scheduleAlarmBroadcast(reminder: Reminder, triggerMillis: Long) {
        val reminderId = reminder.id
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.description)
            putExtra(EXTRA_NOTIFICATION_MODE, reminder.notificationMode.name)
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
            if (!hasExactAlarmPermission()) {
                // Exact alarms were not granted (Android 12+ requires explicit user consent).
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else if (reminder.notificationMode == NotificationMode.NOTIFICATION) {
                // Exact and Doze-proof, without the status-bar alarm icon setAlarmClock() shows.
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                // setAlarmClock() is used instead of setExactAndAllowWhileIdle(): it is exempt
                // from Doze/App Standby *and* from aggressive OEM battery managers (e.g. OnePlus
                // OxygenOS "deep optimization"), which were silently delaying or dropping alarms
                // fired while the app was in the background.
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, showIntent), pendingIntent)
            }
        } catch (e: SecurityException) {
            // Fallback to inexact alarm if exact permission denied
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    /**
     * The first occurrence strictly after [now]. Steps forward repeatedly so a recurring reminder
     * that was due while the phone was off (or the app was killed) catches up instead of
     * computing a time that's already past and silently never firing again.
     */
    private fun nextOccurrence(reminder: Reminder, now: LocalDateTime): LocalDateTime? {
        if (reminder.recurrenceType == RecurrenceType.NONE) return null
        val weekDays = WeekDays.parse(reminder.recurrenceValue)
        var next = step(reminder, weekDays, reminder.reminderDateTime)
        var guard = 0
        while (!next.isAfter(now) && guard++ < 10_000) next = step(reminder, weekDays, next)
        return next
    }

    private fun step(reminder: Reminder, weekDays: Set<java.time.DayOfWeek>, from: LocalDateTime): LocalDateTime =
        when (reminder.recurrenceType) {
            RecurrenceType.DAILY -> from.plusDays(1)
            // Multi-day weekly ("MONDAY,WEDNESDAY") steps to the next selected day; a single day
            // or an empty value (older reminders) is the plain once-a-week case.
            RecurrenceType.WEEKLY ->
                if (weekDays.size <= 1) from.plusWeeks(1)
                else WeekDays.firstOccurrence(from.plusDays(1), weekDays, from)
            RecurrenceType.MONTHLY -> from.plusMonths(1)
            RecurrenceType.YEARLY -> from.plusYears(1)
            RecurrenceType.NONE -> from
        }
}
