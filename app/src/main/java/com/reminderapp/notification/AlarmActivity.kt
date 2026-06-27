package com.reminderapp.notification

import android.app.KeyguardManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.ReminderStatus
import com.reminderapp.scheduler.ReminderScheduler
import com.reminderapp.ui.theme.ReminderAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var scheduler: ReminderScheduler

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupLockScreenFlags()

        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Reminder"
        val description = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION) ?: ""

        startAlarmSound()
        startVibration()

        setContent {
            ReminderAppTheme {
                AlarmScreen(
                    title = title,
                    description = description,
                    onDismiss = {
                        stopAlarm()
                        if (reminderId != -1L) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val reminder = repository.getReminderById(reminderId)
                                reminder?.let { r ->
                                    // 1. Mark current as MISSED
                                    repository.updateStatus(reminderId, ReminderStatus.MISSED)
                                    
                                    // 2. Handle recurrence
                                    if (r.recurrenceType != com.reminderapp.domain.model.RecurrenceType.NONE) {
                                        val nextDate = scheduler.nextOccurrence(r)
                                        if (nextDate != null) {
                                            val nextReminder = r.copy(
                                                id = 0,
                                                reminderDateTime = nextDate,
                                                status = ReminderStatus.ACTIVE,
                                                createdAt = LocalDateTime.now(),
                                                updatedAt = LocalDateTime.now()
                                            )
                                            val newId = repository.insertReminder(nextReminder)
                                            scheduler.schedule(nextReminder.copy(id = newId))
                                        }
                                    }
                                }
                            }
                        }
                        notificationHelper.cancelNotification(reminderId)
                        finish()
                    },
                    onComplete = {
                        stopAlarm()
                        if (reminderId != -1L) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val reminder = repository.getReminderById(reminderId)
                                reminder?.let { r ->
                                    if (r.recurrenceType != com.reminderapp.domain.model.RecurrenceType.NONE) {
                                        if (r.status == ReminderStatus.MISSED) {
                                            repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                                        } else {
                                            // Recurring: Update existing record to next time
                                            val nextDate = scheduler.nextOccurrence(r)
                                            if (nextDate != null) {
                                                val updated = r.copy(
                                                    reminderDateTime = nextDate,
                                                    status = ReminderStatus.ACTIVE,
                                                    updatedAt = LocalDateTime.now()
                                                )
                                                repository.updateReminder(updated)
                                                scheduler.schedule(updated)
                                            }
                                        }
                                    } else {
                                        // One-time: Move to Completed
                                        repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                                    }
                                }
                            }
                        }
                        notificationHelper.cancelNotification(reminderId)
                        finish()
                    },
                    onSnooze = { minutes ->
                        stopAlarm()
                        if (reminderId != -1L) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val reminder = repository.getReminderById(reminderId)
                                reminder?.let {
                                    val snoozedTime = LocalDateTime.now().plusMinutes(minutes.toLong())
                                    val snoozedReminder = it.copy(
                                        reminderDateTime = snoozedTime,
                                        status = ReminderStatus.ACTIVE
                                    )
                                    repository.updateReminder(snoozedReminder)
                                    scheduler.schedule(snoozedReminder)
                                }
                            }
                        }
                        notificationHelper.cancelNotification(reminderId)
                        finish()
                    }
                )
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun startAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VibratorManager::class.java)
                vibrator = vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 500, 500, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    var showSnoozeOptions by remember { mutableStateOf(false) }
    var showCustomSnoozeDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "ALARM",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Mark Complete", fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = { showSnoozeOptions = !showSnoozeOptions },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Snooze", fontSize = 16.sp)
            }

            if (showSnoozeOptions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val options = listOf(
                            5 to "5 minutes",
                            10 to "10 minutes",
                            60 to "1 hour",
                            120 to "2 hours"
                        )
                        
                        options.forEach { (mins, label) ->
                            TextButton(
                                onClick = { onSnooze(mins) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(label)
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        TextButton(
                            onClick = { showCustomSnoozeDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Custom...")
                        }
                    }
                }
            }

            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showCustomSnoozeDialog) {
        CustomSnoozeDialog(
            onDismiss = { showCustomSnoozeDialog = false },
            onConfirm = { mins ->
                showCustomSnoozeDialog = false
                onSnooze(mins)
            }
        )
    }
}

@Composable
private fun CustomSnoozeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutes by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Snooze") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter minutes to snooze:")
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { if (it.all { char -> char.isDigit() }) minutes = it },
                    placeholder = { Text("e.g. 15") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    minutes.toIntOrNull()?.let { onConfirm(it) }
                },
                enabled = minutes.isNotEmpty()
            ) {
                Text("Snooze")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
