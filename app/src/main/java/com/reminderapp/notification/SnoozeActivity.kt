package com.reminderapp.notification

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
class SnoozeActivity : ComponentActivity() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) {
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val reminder = repository.getReminderById(reminderId)
            launch(Dispatchers.Main) {
                if (reminder == null) {
                    finish()
                } else {
                    setContent {
                        ReminderAppTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ) {
                                SnoozeOptionsContent(
                                    title = reminder.title,
                                    onSnooze = { minutes ->
                                        CoroutineScope(Dispatchers.IO).launch {
                                            scheduler.snooze(reminderId, minutes)
                                        }
                                        notificationHelper.cancelNotification(reminderId)
                                        finish()
                                    },
                                    onCancel = { finish() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnoozeOptionsContent(
    title: String,
    onSnooze: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Snooze",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                val options = listOf(
                    5 to "5 minutes",
                    10 to "10 minutes",
                    60 to "1 hour",
                    120 to "2 hours"
                )

                options.forEach { (mins, label) ->
                    Button(
                        onClick = { onSnooze(mins) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(label)
                    }
                }

                OutlinedButton(
                    onClick = { showCustomDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Custom...")
                }

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomSnoozeDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = onSnooze
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
