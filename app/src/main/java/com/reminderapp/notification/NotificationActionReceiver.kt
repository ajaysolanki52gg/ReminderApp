package com.reminderapp.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.ReminderStatus
import com.reminderapp.scheduler.AlarmReceiver
import com.reminderapp.scheduler.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var alarmManager: AlarmManager

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        when (intent.action) {
            NotificationHelper.ACTION_COMPLETE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val reminder = repository.getReminderById(reminderId)
                    reminder?.let { r ->
                        if (r.recurrenceType != com.reminderapp.domain.model.RecurrenceType.NONE) {
                            if (r.status == ReminderStatus.MISSED) {
                                repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                            } else {
                                // Recurring: Update in place
                                val nextDate = scheduler.nextOccurrence(r)
                                if (nextDate != null) {
                                    val updated = r.copy(
                                        reminderDateTime = nextDate,
                                        status = ReminderStatus.ACTIVE,
                                        updatedAt = java.time.LocalDateTime.now()
                                    )
                                    repository.updateReminder(updated)
                                    scheduler.schedule(updated)
                                }
                            }
                        } else {
                            // One-time: Mark as COMPLETED
                            repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                            scheduler.cancel(reminderId)
                        }
                    }
                }
                notificationHelper.cancelNotification(reminderId)
            }

            NotificationHelper.ACTION_SNOOZE -> {
                val snoozeMinutes = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 10)
                notificationHelper.cancelNotification(reminderId)

                val triggerMillis = System.currentTimeMillis() +
                        TimeUnit.MINUTES.toMillis(snoozeMinutes.toLong())

                val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                    putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE,
                        intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Reminder")
                    putExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION,
                        intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION) ?: "")
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (reminderId + 10000).toInt(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } catch (e: SecurityException) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            }
        }
    }
}
