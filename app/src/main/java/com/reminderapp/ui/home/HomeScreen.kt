package com.reminderapp.ui.home

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
    var openWithVoice by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }
    var snoozeReminder by remember { mutableStateOf<Reminder?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            AnimatedVisibility(
                visible = !showAssistantBar,
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
                    HomeHeader(onSettingsClick = onNavigateToSettings)
                    
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
                        openWithVoice = false
                        showAssistantBar = true
                        showActionMenu = false
                    },
                    onCreateVoice = {
                        openWithVoice = true
                        showAssistantBar = true
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
                    onNavigateToAddReminder = onNavigateToAddReminder,
                    initialListening = openWithVoice
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
private fun HomeHeader(onSettingsClick: () -> Unit) {
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "Stay on top of what's important",
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
                    if (onMarkComplete != null) {
                        onMarkComplete()
                        true
                    } else {
                        false
                    }
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
                SwipeToDismissBoxValue.EndToStart -> Color(0xFF3F7656) // AppCompleted
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
                text = "What would you like to do?",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ActionMenuItem(
                icon = Icons.Default.Add,
                title = "Create reminder",
                onClick = onCreateManual
            )
            ActionMenuItem(
                icon = Icons.Default.AutoAwesome,
                title = "Create with AI",
                onClick = onCreateAI,
                iconColor = MaterialTheme.colorScheme.secondary
            )
            ActionMenuItem(
                icon = Icons.Default.Mic,
                title = "Create by voice",
                onClick = onCreateVoice,
                iconColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}
