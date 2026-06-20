package com.reminderapp.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.ReminderStatus
import com.reminderapp.scheduler.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

@HiltWorker
class ReminderNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val title = inputData.getString(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: return Result.failure()
        val description = inputData.getString(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION) ?: ""

        if (reminderId == -1L) return Result.failure()

        notificationHelper.showReminderNotification(reminderId, title, description)

        // Mark missed if not completed, reschedule if recurring
        val reminder = repository.getReminderById(reminderId)
        reminder?.let {
            if (it.status == com.reminderapp.domain.model.ReminderStatus.ACTIVE) {
                repository.updateStatus(reminderId, ReminderStatus.MISSED)
            }
            scheduler.rescheduleRecurring(it)
        }

        return Result.success()
    }
}
