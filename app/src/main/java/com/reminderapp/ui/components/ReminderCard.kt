package com.reminderapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reminderapp.domain.model.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderCard(
    reminder: Reminder,
    onTap: () -> Unit,
    onMarkComplete: (() -> Unit)?,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)?,
    onSnooze: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val isCompleted = reminder.status == ReminderStatus.COMPLETED
    val isMissed = reminder.status == ReminderStatus.MISSED
    
    val (dateTimeText, relativeTimeText) = remember(reminder.reminderDateTime, reminder.status) {
        formatReminderDateTime(reminder.reminderDateTime, reminder.status)
    }

    val accentColor = when {
        isCompleted -> Color(0xFF3F7656)
        isMissed -> Color(0xFFB65C5C)
        else -> MaterialTheme.colorScheme.primary
    }
    
    val onContainerColor = MaterialTheme.colorScheme.onSurface

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                ),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompleted || isMissed) accentColor.copy(alpha = 0.1f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (isMissed) {
                        Icon(
                            Icons.Default.PriorityHigh,
                            contentDescription = "Missed",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        IconButton(
                            onClick = { onMarkComplete?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "Mark complete",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCompleted) onContainerColor.copy(alpha = 0.5f) else onContainerColor
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = dateTimeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMissed) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (relativeTimeText != null && !isCompleted) {
                        Text(
                            text = relativeTimeText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isMissed) accentColor else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            if (!isCompleted && onMarkComplete != null) {
                DropdownMenuItem(
                    text = { Text("Mark Complete") },
                    onClick = {
                        onMarkComplete()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                )
            }
            if (!isCompleted && onSnooze != null) {
                DropdownMenuItem(
                    text = { Text("Snooze") },
                    onClick = {
                        onSnooze()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                )
            }
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        onEdit()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDelete()
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}

private fun formatReminderDateTime(dateTime: LocalDateTime, status: ReminderStatus): Pair<String, String?> {
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val reminderDate = dateTime.toLocalDate()
    
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val timeText = dateTime.format(timeFormatter)
    
    val dateText = when {
        reminderDate == today -> "Today"
        reminderDate == today.plusDays(1) -> "Tomorrow"
        reminderDate == today.minusDays(1) -> "Yesterday"
        reminderDate.year == today.year -> dateTime.format(DateTimeFormatter.ofPattern("EEE · MMM d"))
        else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
    
    val mainText = "$dateText · $timeText"
    
    var relativeText: String? = null
    if (status == ReminderStatus.ACTIVE) {
        val duration = Duration.between(now, dateTime)
        val minutes = duration.toMinutes()
        
        relativeText = when {
            minutes < 0 -> null
            minutes < 60 -> "in $minutes min"
            minutes < 1440 -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "in ${hours}h ${remainingMinutes}m" else "in ${hours}h"
            }
            else -> {
                val days = minutes / 1440
                "in $days days"
            }
        }
    } else if (status == ReminderStatus.MISSED) {
        val duration = Duration.between(dateTime, now)
        val minutes = duration.toMinutes()
        relativeText = when {
            minutes < 60 -> "Missed $minutes min ago"
            minutes < 1440 -> "Missed ${minutes / 60}h ago"
            else -> "Missed ${minutes / 1440}d ago"
        }
    }
    
    return Pair(mainText, relativeText)
}
