package com.reminderapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.ReminderStatus
import com.reminderapp.scheduler.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        when (intent.action) {
            NotificationHelper.ACTION_COMPLETE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                    // Without this, completing a recurring reminder from the plain notification's
                    // "Complete" action (as opposed to the full-screen AlarmActivity) never
                    // scheduled the next occurrence at all - it just stopped recurring silently.
                    val reminder = repository.getReminderById(reminderId)
                    reminder?.let { scheduler.rescheduleRecurring(it) }
                }
                notificationHelper.cancelNotification(reminderId)
            }

            NotificationHelper.ACTION_SNOOZE -> {
                val snoozeMinutes = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 10)
                notificationHelper.cancelNotification(reminderId)
                CoroutineScope(Dispatchers.IO).launch {
                    scheduler.snooze(reminderId, snoozeMinutes)
                }
            }
        }
    }
}
