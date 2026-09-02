package com.reminderapp.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens the system Settings screens needed for Alarm-mode reminders to fire reliably: exact
 * alarms, and (Android 14+) full-screen intent. Only call this when the user actually opts into
 * Alarm mode - these permissions are irrelevant to the Notification mode, so asking for them
 * proactively on every app launch would nag users who never use alarms.
 */
fun Context.requestAlarmReliabilityPermissionsIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager?.canScheduleExactAlarms() == false) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager?.canUseFullScreenIntent() == false) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
