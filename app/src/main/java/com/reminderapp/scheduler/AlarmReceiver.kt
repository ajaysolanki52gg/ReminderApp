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
