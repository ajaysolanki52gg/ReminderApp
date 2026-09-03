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
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.ReminderStatus
import com.reminderapp.scheduler.ReminderScheduler
import com.reminderapp.ui.components.MinutesInputDialog
import com.reminderapp.ui.theme.ReminderAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                                repository.updateStatus(reminderId, ReminderStatus.MISSED)
                                val reminder = repository.getReminderById(reminderId)
                                reminder?.let { scheduler.rescheduleRecurring(it) }
                            }
                        }
                        notificationHelper.cancelNotification(reminderId)
                        finish()
                    },
                    onComplete = {
                        stopAlarm()
                        if (reminderId != -1L) {
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                                val reminder = repository.getReminderById(reminderId)
                                reminder?.let { scheduler.rescheduleRecurring(it) }
                            }
                        }
                        notificationHelper.cancelNotification(reminderId)
                        finish()
                    },
                    onSnooze = { minutes ->
                        stopAlarm()
                        if (reminderId != -1L) {
                            CoroutineScope(Dispatchers.IO).launch {
                                scheduler.snooze(reminderId, minutes)
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
    var showCustomSnooze by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Mark Complete", fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = { showSnoozeOptions = !showSnoozeOptions },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Snooze", fontSize = 16.sp)
            }

            if (showSnoozeOptions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        listOf(5, 10, 30, 60).forEach { minutes ->
                            TextButton(
                                onClick = { onSnooze(minutes) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (minutes < 60) "$minutes minutes" else "1 hour")
                            }
                        }
                        TextButton(
                            onClick = { showCustomSnooze = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Custom")
                        }
                    }
                }
            }

            if (showCustomSnooze) {
                MinutesInputDialog(
                    onDismiss = { showCustomSnooze = false },
                    onConfirm = { minutes ->
                        showCustomSnooze = false
                        onSnooze(minutes)
                    }
                )
            }

            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
