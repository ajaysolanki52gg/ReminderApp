package com.reminderapp.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderapp.notification.AlarmActivity
import com.reminderapp.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: return
        val description = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION) ?: ""

        if (reminderId == -1L) return

        // Launch full-screen alarm activity
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, title)
            putExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION, description)
        }
        context.startActivity(alarmIntent)

        // Also show notification in case screen is locked
        notificationHelper.showAlarmNotification(reminderId, title, description)
    }
}
