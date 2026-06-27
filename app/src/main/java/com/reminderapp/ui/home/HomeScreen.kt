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
import com.reminderapp.ui.components.VoiceAssistantOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddReminder: () -> Unit,
    onNavigateToAddReminderWithText: (String) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAssistantBar by remember { mutableStateOf(false) }
    var showVoiceOverlay by remember { mutableStateOf(false) }
    var initialTextForAssistant by remember { mutableStateOf("") }
    
    var seeAllUpcoming by remember { mutableStateOf(false) }
    var seeAllCompleted by remember { mutableStateOf(false) }
    var seeAllMissed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedIds.size} Selected",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
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
            }
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
                            showVoiceOverlay = true
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
                            onClick = { 
                                initialTextForAssistant = ""
                                showAssistantBar = true 
                            },
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
                        val upcomingToShow = if (seeAllUpcoming) uiState.upcomingReminders else uiState.upcomingReminders.take(5)
                        items(
                            items = upcomingToShow,
                            key = { it.id }
                        ) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onTap = { 
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleSelection(reminder.id)
                                    } else {
                                        onNavigateToDetail(reminder.id)
                                    }
                                },
                                onLongPress = { viewModel.toggleSelection(reminder.id) },
                                isSelected = uiState.selectedIds.contains(reminder.id),
                                isSelectionMode = uiState.isSelectionMode,
                                onMarkComplete = { viewModel.markComplete(reminder.id) },
                                onDelete = { viewModel.deleteReminder(reminder.id) },
                                onEdit = { onNavigateToAddReminder() }
                            )
                        }
                        
                        if (uiState.upcomingReminders.size > 5 && !seeAllUpcoming) {
                            item {
                                TextButton(
                                    onClick = { seeAllUpcoming = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("See All Upcoming (${uiState.upcomingReminders.size})")
                                }
                            }
                        } else if (seeAllUpcoming) {
                            item {
                                TextButton(
                                    onClick = { seeAllUpcoming = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Show Less")
                                }
                            }
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
                            val completedToShow = if (seeAllCompleted) uiState.completedReminders else uiState.completedReminders.take(5)
                            items(
                                items = completedToShow,
                                key = { "completed_${it.id}" }
                            ) { reminder ->
                                ReminderCard(
                                    reminder = reminder,
                                    onTap = { 
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelection(reminder.id)
                                        } else {
                                            onNavigateToDetail(reminder.id)
                                        }
                                    },
                                    onLongPress = { viewModel.toggleSelection(reminder.id) },
                                    isSelected = uiState.selectedIds.contains(reminder.id),
                                    isSelectionMode = uiState.isSelectionMode,
                                    onMarkComplete = null,
                                    onDelete = { viewModel.deleteReminder(reminder.id) },
                                    onEdit = null
                                )
                            }

                            if (uiState.completedReminders.size > 5 && !seeAllCompleted) {
                                item {
                                    TextButton(
                                        onClick = { seeAllCompleted = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("See All Completed (${uiState.completedCount})")
                                    }
                                }
                            } else if (seeAllCompleted) {
                                item {
                                    TextButton(
                                        onClick = { seeAllCompleted = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Show Less")
                                    }
                                }
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
                            val missedToShow = if (seeAllMissed) uiState.missedReminders else uiState.missedReminders.take(5)
                            items(
                                items = missedToShow,
                                key = { "missed_${it.id}" }
                            ) { reminder ->
                                ReminderCard(
                                    reminder = reminder,
                                    onTap = { 
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelection(reminder.id)
                                        } else {
                                            onNavigateToDetail(reminder.id)
                                        }
                                    },
                                    onLongPress = { viewModel.toggleSelection(reminder.id) },
                                    isSelected = uiState.selectedIds.contains(reminder.id),
                                    isSelectionMode = uiState.isSelectionMode,
                                    onMarkComplete = { viewModel.markComplete(reminder.id) },
                                    onDelete = { viewModel.deleteReminder(reminder.id) },
                                    onEdit = { onNavigateToAddReminder() }
                                )
                            }

                            if (uiState.missedReminders.size > 5 && !seeAllMissed) {
                                item {
                                    TextButton(
                                        onClick = { seeAllMissed = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("See All Missed (${uiState.missedCount})")
                                    }
                                }
                            } else if (seeAllMissed) {
                                item {
                                    TextButton(
                                        onClick = { seeAllMissed = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Show Less")
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
                    initialText = initialTextForAssistant,
                    onVoiceRequest = { 
                        showAssistantBar = false
                        showVoiceOverlay = true 
                    }
                )
            }
        }
    }

    if (showVoiceOverlay) {
        VoiceAssistantOverlay(
            onDismiss = { showVoiceOverlay = false },
            onResult = { result ->
                showVoiceOverlay = false
                onNavigateToAddReminderWithText(result)
            }
        )
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
