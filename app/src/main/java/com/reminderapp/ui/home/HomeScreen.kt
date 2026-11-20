package com.reminderapp.ui.home

import com.reminderapp.ui.theme.StatusColors

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reminderapp.domain.model.Reminder
import com.reminderapp.ui.components.AssistantInputBar
import com.reminderapp.ui.components.VoiceAssistantOverlay
import com.reminderapp.ui.components.ReminderCard
import com.reminderapp.ui.components.SectionHeader
import com.reminderapp.ui.components.SnoozeSelector
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddReminder: () -> Unit,
    onNavigateToEditReminder: (Long) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAssistantBar by remember { mutableStateOf(false) }
    var showVoiceOverlay by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }
    var snoozeReminder by remember { mutableStateOf<Reminder?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            AnimatedVisibility(
                visible = !showAssistantBar && !showVoiceOverlay,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(
                    onClick = { showActionMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .size(64.dp)
                        .imePadding()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    HomeHeader(
                        upcomingCount = uiState.upcomingReminders.size,
                        onSettingsClick = onNavigateToSettings
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReminderSection(
                            title = "Upcoming",
                            reminders = uiState.upcomingReminders,
                            isExpanded = true,
                            onToggle = null,
                            onNavigateToDetail = onNavigateToDetail,
                            onMarkComplete = { viewModel.markComplete(it) },
                            onDelete = { viewModel.deleteReminder(it) },
                            onEdit = { onNavigateToEditReminder(it) },
                            onSnooze = { snoozeReminder = it }
                        )

                        ReminderSection(
                            title = "Completed",
                            reminders = uiState.completedReminders,
                            isExpanded = uiState.isCompletedExpanded,
                            onToggle = { viewModel.toggleCompletedExpanded() },
                            onNavigateToDetail = onNavigateToDetail,
                            onMarkComplete = null,
                            onDelete = { viewModel.deleteReminder(it) },
                            onEdit = null,
                            onSnooze = null
                        )

                        ReminderSection(
                            title = "Missed",
                            reminders = uiState.missedReminders,
                            isExpanded = uiState.isMissedExpanded,
                            onToggle = { viewModel.toggleMissedExpanded() },
                            onNavigateToDetail = onNavigateToDetail,
                            onMarkComplete = { viewModel.markComplete(it) },
                            onDelete = { viewModel.deleteReminder(it) },
                            onEdit = { onNavigateToEditReminder(it) },
                            onSnooze = { snoozeReminder = it }
                        )
                        
                        Spacer(Modifier.height(120.dp))
                    }
                }
            }

            if (showActionMenu) {
                ActionSelectionMenu(
                    onDismiss = { showActionMenu = false },
                    onCreateManual = {
                        onNavigateToAddReminder()
                        showActionMenu = false
                    },
                    onCreateAI = {
                        showAssistantBar = true
                        showActionMenu = false
                    },
                    onCreateVoice = {
                        showVoiceOverlay = true
                        showActionMenu = false
                    }
                )
            }

            AnimatedVisibility(
                visible = showAssistantBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                AssistantInputBar(
                    onDismiss = { showAssistantBar = false },
                    onNavigateToAddReminder = onNavigateToAddReminder
                )
            }

            if (showVoiceOverlay) {
                VoiceAssistantOverlay(
                    onDismiss = { showVoiceOverlay = false },
                    onNavigateToAddReminder = onNavigateToAddReminder
                )
            }
            
            if (snoozeReminder != null) {
                ModalBottomSheet(
                    onDismissRequest = { snoozeReminder = null },
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    SnoozeSelector(
                        title = snoozeReminder?.title ?: "",
                        onSnoozeMinutes = { mins ->
                            snoozeReminder?.let { viewModel.snoozeReminder(it.id, mins) }
                            snoozeReminder = null
                        },
                        onSnoozeDateTime = { dateTime ->
                            snoozeReminder?.let { viewModel.snoozeReminder(it.id, dateTime) }
                            snoozeReminder = null
                        },
                        onCancel = { snoozeReminder = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(upcomingCount: Int, onSettingsClick: () -> Unit) {
    val today = remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reminders",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = when (upcomingCount) {
                0 -> "$today · all clear"
                1 -> "$today · 1 upcoming"
                else -> "$today · $upcomingCount upcoming"
            },
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    title: String,
    reminders: List<Reminder>,
    isExpanded: Boolean,
    onToggle: (() -> Unit)?,
    onNavigateToDetail: (Long) -> Unit,
    onMarkComplete: ((Long) -> Unit)?,
    onDelete: (Long) -> Unit,
    onEdit: ((Long) -> Unit)?,
    onSnooze: ((Reminder) -> Unit)?
) {
    Column {
        SectionHeader(
            title = title,
            count = reminders.size,
            isExpanded = isExpanded,
            onToggle = onToggle
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (reminders.isEmpty()) {
                EmptyState(title = title)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    reminders.forEach { reminder ->
                        // key(): without it the swipe state belongs to the list *position*, so
                        // after one card is swiped away the next card inherits "dismissed".
                        key(reminder.id) {
                        SwipeableReminderItem(
                            reminder = reminder,
                            onTap = { onNavigateToDetail(reminder.id) },
                            onMarkComplete = if (onMarkComplete != null) { { onMarkComplete(reminder.id) } } else null,
                            onDelete = { onDelete(reminder.id) },
                            onEdit = if (onEdit != null) { { onEdit(reminder.id) } } else null,
                            onSnooze = if (onSnooze != null) { { onSnooze(reminder) } } else null
                        )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableReminderItem(
    reminder: Reminder,
    onTap: () -> Unit,
    onMarkComplete: (() -> Unit)?,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)?,
    onSnooze: (() -> Unit)?
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSnooze?.invoke()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Return false so the card snaps back; the list update moves it to
                    // Completed (or, for a recurring reminder, to its next occurrence).
                    onMarkComplete?.invoke()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                SwipeToDismissBoxValue.EndToStart -> StatusColors.completed
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                    Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                        Icons.Default.Schedule else Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        },
        content = {
            ReminderCard(
                reminder = reminder,
                onTap = onTap,
                onMarkComplete = onMarkComplete,
                onDelete = onDelete,
                onEdit = onEdit,
                onSnooze = onSnooze
            )
        }
    )
}

@Composable
private fun EmptyState(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val icon = when (title) {
            "Upcoming" -> "✦"
            "Completed" -> "✨"
            else -> "🎉"
        }
        val message = when (title) {
            "Upcoming" -> "Nothing coming up"
            "Completed" -> "No completed reminders"
            else -> "Nothing missed"
        }
        val subMessage = when (title) {
            "Upcoming" -> "You're all caught up"
            else -> ""
        }
        
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 8.dp)
        )
        if (subMessage.isNotEmpty()) {
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSelectionMenu(
    onDismiss: () -> Unit,
    onCreateManual: () -> Unit,
    onCreateAI: () -> Unit,
    onCreateVoice: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "New reminder",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ActionMenuItem(
                icon = Icons.Default.Mic,
                title = "Say it",
                subtitle = "“Kal subah 9 baje doctor ko call karna”",
                onClick = onCreateVoice,
                iconColor = MaterialTheme.colorScheme.onPrimary,
                iconBackground = MaterialTheme.colorScheme.primary
            )
            ActionMenuItem(
                icon = Icons.Default.AutoAwesome,
                title = "Type it",
                subtitle = "“Pay rent every month on 1st”",
                onClick = onCreateAI,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                iconBackground = MaterialTheme.colorScheme.secondaryContainer
            )
            ActionMenuItem(
                icon = Icons.Default.EditCalendar,
                title = "Fill in a form",
                subtitle = "Pick the date and time yourself",
                onClick = onCreateManual
            )
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
