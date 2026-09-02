package com.reminderapp.util

import com.reminderapp.domain.model.Reminder
import javax.inject.Inject
import javax.inject.Singleton

// AddReminderViewModel is scoped per navigation destination, so a not-yet-saved Reminder parsed
// on the Home route can't be handed to it directly - it would be prefilling a different instance
// than the one AddReminderScreen actually reads from after navigating. This bridges that gap.
@Singleton
class PendingReminderHolder @Inject constructor() {
    private var pending: Reminder? = null

    fun set(reminder: Reminder) {
        pending = reminder
    }

    fun consume(): Reminder? = pending.also { pending = null }
}
