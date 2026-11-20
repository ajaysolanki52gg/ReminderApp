package com.reminderapp.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.NotificationMode
import com.reminderapp.domain.model.RecurrenceType
import com.reminderapp.domain.model.ReminderStatus
import com.reminderapp.notification.AlarmActivity
import com.reminderapp.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: return
        val description = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION) ?: ""
        // Alarms scheduled by older builds carry no mode extra; those were all ALARM mode.
        val mode = intent.getStringExtra(ReminderScheduler.EXTRA_NOTIFICATION_MODE)
            ?.let { runCatching { NotificationMode.valueOf(it) }.getOrNull() }
            ?: NotificationMode.ALARM

        if (reminderId == -1L) return

        when (mode) {
            NotificationMode.NOTIFICATION -> showNotification(reminderId, title, description)
            NotificationMode.ALARM -> showAlarm(context, reminderId, title, description)
        }
    }

    private fun showNotification(reminderId: Long, title: String, description: String) {
        notificationHelper.showReminderNotification(reminderId, title, description)

        // DB work must finish before the receiver returns, otherwise the process can be
        // killed mid-write. goAsync() keeps it alive (up to ~10s) until finish() is called.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = repository.getReminderById(reminderId) ?: return@launch
                when {
                    // A snoozed recurring reminder: go back to its regular series.
                    reminder.snoozedUntil != null && reminder.recurrenceType != RecurrenceType.NONE ->
                        scheduler.resumeSeries(reminder)
                    // Recurring: move on to (and schedule) the next occurrence right away so the
                    // series keeps going even if the user never touches this notification.
                    reminder.recurrenceType != RecurrenceType.NONE ->
                        scheduler.rescheduleRecurring(reminder)
                    reminder.status == ReminderStatus.ACTIVE ->
                        repository.updateStatus(reminderId, ReminderStatus.MISSED)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showAlarm(context: Context, reminderId: Long, title: String, description: String) {
        // Show the alarm notification first: it carries a full-screen intent, which is the
        // delivery path the system actually guarantees. A direct context.startActivity() call
        // from a BroadcastReceiver is blocked by Android 10+ background-activity-launch
        // restrictions (and can throw on newer Android versions), which is why alarms were
        // sometimes silent when the app wasn't already in the foreground.
        notificationHelper.showAlarmNotification(reminderId, title, description)

        // Best-effort direct launch, useful when the app happens to already be in the foreground.
        try {
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, title)
                putExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION, description)
            }
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
