package com.reminderapp.ui.components

import androidx.compose.foundation.BorderStroke
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
import com.reminderapp.ui.theme.StatusColors
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
private fun MetaBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String?) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label ?: "Alarm",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (label != null) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

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
        isCompleted -> StatusColors.completed
        isMissed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val onContainerColor = MaterialTheme.colorScheme.onSurface

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                ),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator / complete toggle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompleted || isMissed) accentColor.copy(alpha = 0.14f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isCompleted -> Icon(
                            Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        isMissed -> Icon(
                            Icons.Default.PriorityHigh,
                            contentDescription = "Missed",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        else -> IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMarkComplete?.invoke()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "Mark complete",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCompleted) onContainerColor.copy(alpha = 0.5f) else onContainerColor
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = dateTimeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMissed) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (reminder.recurrenceType != RecurrenceType.NONE) {
                            MetaBadge(
                                icon = Icons.Default.Repeat,
                                label = reminder.recurrenceLabel()
                            )
                        }
                        if (reminder.notificationMode == NotificationMode.ALARM) {
                            MetaBadge(icon = Icons.Default.Alarm, label = null)
                        }
                    }

                    reminder.snoozedUntil?.let { until ->
                        Text(
                            text = "Snoozed until ${until.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (isMissed && relativeTimeText != null) {
                        Text(
                            text = relativeTimeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                if (!isCompleted && !isMissed && relativeTimeText != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = relativeTimeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
            minutes < 1 -> "now"
            minutes < 60 -> "in $minutes min"
            minutes < 1440 -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "in ${hours}h ${remainingMinutes}m" else "in ${hours}h"
            }
            else -> {
                val days = minutes / 1440
                if (days == 1L) "in 1 day" else "in $days days"
            }
        }
    } else if (status == ReminderStatus.MISSED) {
        val duration = Duration.between(dateTime, now)
        val minutes = duration.toMinutes()
        relativeText = when {
            minutes < 1 -> "Missed just now"
            minutes < 60 -> "Missed $minutes min ago"
            minutes < 1440 -> "Missed ${minutes / 60}h ago"
            else -> "Missed ${minutes / 1440}d ago"
        }
    }
    
    return Pair(mainText, relativeText)
}
