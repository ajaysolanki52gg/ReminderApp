package com.reminderapp.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reminderapp.domain.model.Reminder
import com.reminderapp.ui.components.AssistantInputBar
import com.reminderapp.ui.components.ReminderCard
import com.reminderapp.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddReminder: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAssistantBar by remember { mutableStateOf(false) }
    var startListeningTrigger by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reminders",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Hide FABs when Assistant is open to avoid congestion
            AnimatedVisibility(
                visible = !showAssistantBar,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.imePadding()
                ) {
                    // 1. Mic FAB (Speak First) - Top of Triangle
                    FloatingActionButton(
                        onClick = {
                            showAssistantBar = true
                            startListeningTrigger++
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Speak", modifier = Modifier.size(28.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 2. Assistant / Bot FAB
                        FloatingActionButton(
                            onClick = { showAssistantBar = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Assistant")
                        }

                        // 3. Add reminder FAB (+)
                        FloatingActionButton(
                            onClick = onNavigateToAddReminder,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // ... (rest of the Box content remains the same)
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (showAssistantBar) 240.dp else 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ... (rest of items)
                    // ─── Upcoming Section ──────────────────────────────────────
                    item {
                        SectionHeader(
                            title = "Upcoming",
                            count = uiState.upcomingReminders.size,
                            isExpanded = true,
                            onToggle = null // Always expanded on home
                        )
                    }

                    if (uiState.upcomingReminders.isEmpty()) {
                        item {
                            EmptyState(
                                message = "🌱 Nothing planned yet",
                                subMessage = "Tap + to create your first reminder"
                            )
                        }
                    } else {
                        items(
                            items = uiState.upcomingReminders,
                            key = { it.id }
                        ) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onTap = { onNavigateToDetail(reminder.id) },
                                onMarkComplete = { viewModel.markComplete(reminder.id) },
                                onDelete = { viewModel.deleteReminder(reminder.id) },
                                onEdit = { onNavigateToAddReminder() }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }

                    // ─── Completed Section ─────────────────────────────────────
                    item {
                        SectionHeader(
                            title = "Completed",
                            count = uiState.completedCount,
                            isExpanded = uiState.isCompletedExpanded,
                            onToggle = { viewModel.toggleCompletedExpanded() }
                        )
                    }

                    if (uiState.isCompletedExpanded) {
                        if (uiState.completedReminders.isEmpty()) {
                            item {
                                EmptyState(
                                    message = "✨ You're all caught up",
                                    subMessage = ""
                                )
                            }
                        } else {
                            items(
                                items = uiState.completedReminders.take(10),
                                key = { "completed_${it.id}" }
                            ) { reminder ->
                                ReminderCard(
                                    reminder = reminder,
                                    onTap = { onNavigateToDetail(reminder.id) },
                                    onMarkComplete = null,
                                    onDelete = { viewModel.deleteReminder(reminder.id) },
                                    onEdit = null
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }

                    // ─── Missed Section ────────────────────────────────────────
                    item {
                        SectionHeader(
                            title = "Missed",
                            count = uiState.missedCount,
                            isExpanded = uiState.isMissedExpanded,
                            onToggle = { viewModel.toggleMissedExpanded() }
                        )
                    }

                    if (uiState.isMissedExpanded) {
                        if (uiState.missedReminders.isEmpty()) {
                            item {
                                EmptyState(
                                    message = "All clear",
                                    subMessage = "No missed reminders"
                                )
                            }
                        } else {
                            items(
                                items = uiState.missedReminders.take(10),
                                key = { "missed_${it.id}" }
                            ) { reminder ->
                                ReminderCard(
                                    reminder = reminder,
                                    onTap = { onNavigateToDetail(reminder.id) },
                                    onMarkComplete = { viewModel.markComplete(reminder.id) },
                                    onDelete = { viewModel.deleteReminder(reminder.id) },
                                    onEdit = { onNavigateToAddReminder() }
                                )
                            }
                            if (uiState.missedCount > 10) {
                                item {
                                    TextButton(onClick = { /* See all */ }) {
                                        Text("See All (${uiState.missedCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Assistant Input Bar ───────────────────────────────────────────
            AnimatedVisibility(
                visible = showAssistantBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                AssistantInputBar(
                    onDismiss = { showAssistantBar = false },
                    onNavigateToAddReminder = onNavigateToAddReminder,
                    initialListening = startListeningTrigger > 0
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    subMessage: String = ""
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subMessage.isNotEmpty()) {
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
