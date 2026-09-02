package com.reminderapp.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminderapp.domain.model.Reminder
import com.reminderapp.domain.model.RecurrenceType
import com.reminderapp.ui.addreminder.AddReminderViewModel
import com.reminderapp.ui.theme.MutedAmber
import com.reminderapp.ui.theme.MutedAmberDark
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantInputBar(
    onDismiss: () -> Unit,
    onNavigateToAddReminder: () -> Unit,
    initialListening: Boolean = false,
    viewModel: AddReminderViewModel = hiltViewModel()
) {
    var inputText by remember { mutableStateOf("") }
    var parsedReminder by remember { mutableStateOf<Reminder?>(null) }
    var parseConfidence by remember { mutableFloatStateOf(1f) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceInput()
    }

    LaunchedEffect(initialListening) {
        if (initialListening) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val speechState by viewModel.speechState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()

    // Stop any in-progress recognition session when the bar is dismissed/leaves composition -
    // otherwise the mic keeps listening in the background, and the next time the bar opens,
    // startListening() silently no-ops because a session is still "active" from before.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopVoiceInput() }
    }

    // Central submit action shared by the Enter/Send IME action, the hardware/OEM-keyboard Enter
    // key fallback below, and the trailing "parse" button, so all three behave identically.
    val submit: () -> Unit = {
        if (inputText.isNotBlank()) {
            val result = viewModel.parseInput(inputText)
            parsedReminder = viewModel.buildReminderFromParse(result)
            parseConfidence = result.confidence
            keyboardController?.hide()
        }
    }

    // Handle voice result
    LaunchedEffect(speechState) {
        when (val state = speechState) {
            is com.reminderapp.speech.SpeechState.PartialResult -> {
                inputText = state.text
            }
            is com.reminderapp.speech.SpeechState.Result -> {
                inputText = state.text
            }
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Hint text, replaced by a live waveform while the mic is listening.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp, end = 32.dp)
                ) {
                    if (isListening) {
                        VoiceLevelIndicator(level = audioLevel)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Listening…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Try: \"Pay electricity bill tomorrow 9am\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            // Fallback for OEM keyboards (common on OnePlus/other custom ROMs)
                            // that deliver a raw Enter key event instead of firing the IME
                            // "send" action, which was making the Enter key appear to do nothing.
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    submit()
                                    true
                                } else {
                                    false
                                }
                            },
                        placeholder = { 
                            Text(
                                "✨ Tell me what to remember...",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            ) 
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (isListening) {
                                        viewModel.stopVoiceInput()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (isListening) "Stop voice input" else "Voice input",
                                    tint = if (isListening) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    FilledIconButton(
                        onClick = { submit() },
                        enabled = inputText.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Parse")
                    }
                }
            }
        }
    }

    // Confirmation Bottom Sheet
    parsedReminder?.let { reminder ->
        ConfirmationSheet(
            reminder = reminder,
            confidence = parseConfidence,
            onSave = {
                viewModel.saveReminder(reminder)
                onDismiss()
            },
            onEdit = {
                viewModel.stashForEdit(reminder)
                onNavigateToAddReminder()
                onDismiss()
            },
            onCancel = { parsedReminder = null }
        )
    }
}

/** Small animated equalizer-style bars whose heights react to live mic input [level] (0f-1f). */
@Composable
private fun VoiceLevelIndicator(level: Float, modifier: Modifier = Modifier) {
    val barWeights = listOf(0.5f, 1f, 0.7f, 0.9f)
    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        barWeights.forEach { weight ->
            val target = (0.25f + level * weight).coerceIn(0.2f, 1f)
            val animatedFraction by animateFloatAsState(targetValue = target, label = "voiceBar")
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(animatedFraction)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(
    reminder: Reminder,
    confidence: Float = 1f,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Confirm Reminder",
                style = MaterialTheme.typography.titleLarge
            )

            if (confidence < 0.8f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.PriorityHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSystemInDarkTheme()) MutedAmberDark else MutedAmber
                    )
                    Text(
                        text = "Some details were guessed — double check the date/time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            ConfirmRow(label = "Title", value = reminder.title)

            reminder.reminderDateTime.let { dt ->
                ConfirmRow(label = "Date", value = dt.format(dateFormatter))
                ConfirmRow(label = "Time", value = dt.format(timeFormatter))
            }

            ConfirmRow(
                label = "Type",
                value = when (reminder.recurrenceType) {
                    RecurrenceType.NONE -> "One-time"
                    RecurrenceType.DAILY -> "Every day"
                    RecurrenceType.WEEKLY -> "Every week"
                    RecurrenceType.MONTHLY -> "Every month"
                    RecurrenceType.YEARLY -> "Every year"
                }
            )

            ConfirmRow(
                label = "Mode",
                value = reminder.notificationMode.name.lowercase().replaceFirstChar { it.uppercase() }
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }
    }
}

@Composable
fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
