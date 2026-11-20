package com.reminderapp.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.RecurrenceType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ReminderRepository

    @Inject
    lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // Alarms don't survive a reboot or app update. goAsync() keeps the receiver alive until
        // every reminder has been re-registered.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = LocalDateTime.now()
                repository.getAllActiveReminders().forEach { reminder ->
                    when {
                        reminder.nextTriggerTime.isAfter(now) -> scheduler.schedule(reminder)
                        // A recurring reminder that came due while the phone was off: jump to
                        // its next future occurrence instead of dropping the whole series.
                        reminder.recurrenceType != RecurrenceType.NONE ->
                            scheduler.rescheduleRecurring(reminder)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
