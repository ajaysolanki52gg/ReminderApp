package com.reminderapp.ui.reminderdetail

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.*
import com.reminderapp.scheduler.ReminderScheduler
import com.reminderapp.ui.theme.MutedAmberContainer
import com.reminderapp.ui.theme.MutedAmberContainerDark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler
) : ViewModel() {

    private val _reminder = MutableStateFlow<Reminder?>(null)
    val reminder: StateFlow<Reminder?> = _reminder.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun loadReminder(id: Long) {
        repository.getReminderByIdFlow(id)
            .onEach { _reminder.value = it }
            .launchIn(viewModelScope)
    }

    fun markComplete() {
        val r = _reminder.value ?: return
        viewModelScope.launch {
            repository.updateStatus(r.id, ReminderStatus.COMPLETED)
            scheduler.cancel(r.id)
            scheduler.rescheduleRecurring(r)
        }
    }

    fun delete() {
        val r = _reminder.value ?: return
        viewModelScope.launch {
            scheduler.cancel(r.id)
            repository.deleteReminder(r.id)
            _deleted.value = true
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(
    reminderId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: ReminderDetailViewModel = hiltViewModel()
) {
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(reminderId) { viewModel.loadReminder(reminderId) }
    LaunchedEffect(deleted) { if (deleted) onNavigateBack() }

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminder Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(reminderId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { innerPadding ->
        reminder?.let { r ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status badge
                AssistChip(
                    onClick = {},
                    label = { Text(r.status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingIcon = {
                        Icon(
                            when (r.status) {
                                ReminderStatus.ACTIVE -> Icons.Outlined.RadioButtonUnchecked
                                ReminderStatus.COMPLETED -> Icons.Default.CheckCircle
                                ReminderStatus.MISSED -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (r.status) {
                            ReminderStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                            ReminderStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                            // Amber, not red: red is reserved for destructive actions (Delete) -
                            // "missed" elsewhere in the app (ReminderCard, section headers) uses
                            // the same amber language, so this chip now matches.
                            ReminderStatus.MISSED -> if (isSystemInDarkTheme()) MutedAmberContainerDark else MutedAmberContainer
                        }
                    )
                )

                // Title
                Text(
                    text = r.title,
                    style = MaterialTheme.typography.headlineSmall
                )

                // Description
                if (r.description.isNotEmpty()) {
                    Text(
                        text = r.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // Details
                DetailRow(
                    icon = Icons.Default.CalendarMonth,
                    label = "Date",
                    value = r.reminderDateTime.format(dateFormatter)
                )
                DetailRow(
                    icon = Icons.Default.Schedule,
                    label = "Time",
                    value = r.reminderDateTime.format(timeFormatter)
                )
                DetailRow(
                    icon = Icons.Default.Repeat,
                    label = "Recurrence",
                    value = when (r.recurrenceType) {
                        RecurrenceType.NONE -> "One-time"
                        RecurrenceType.DAILY -> "Every day"
                        RecurrenceType.WEEKLY -> "Every week (${r.recurrenceValue.lowercase().replaceFirstChar { it.uppercase() }})"
                        RecurrenceType.MONTHLY -> "Every month on ${r.recurrenceValue}${ordinalSuffix(r.recurrenceValue.toIntOrNull() ?: 1)}"
                        RecurrenceType.YEARLY -> "Every year on ${r.recurrenceValue.replace("_", " ")}"
                    }
                )
                DetailRow(
                    icon = if (r.notificationMode == NotificationMode.NOTIFICATION)
                        Icons.Default.Notifications else Icons.Default.Alarm,
                    label = "Mode",
                    value = r.notificationMode.name.lowercase().replaceFirstChar { it.uppercase() }
                )

                Spacer(Modifier.weight(1f))

                // Actions
                if (r.status == ReminderStatus.ACTIVE || r.status == ReminderStatus.MISSED) {
                    Button(
                        onClick = { viewModel.markComplete() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mark as Complete")
                    }
                }
            }
        } ?: Box(
            Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Reminder") },
            text = { Text("This reminder will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(); showDeleteDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun ordinalSuffix(n: Int): String = when {
    n in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}
