package com.reminderapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.RecurrenceType
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
                notificationHelper.cancelNotification(reminderId)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val reminder = repository.getReminderById(reminderId) ?: return@launch
                        // A recurring reminder was already advanced to (and scheduled for) its
                        // next occurrence when this notification fired. Marking it COMPLETED or
                        // advancing it again here would silently skip that next occurrence.
                        if (reminder.recurrenceType == RecurrenceType.NONE) {
                            repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
