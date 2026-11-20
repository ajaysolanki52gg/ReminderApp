package com.reminderapp.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminderapp.domain.model.Reminder
import com.reminderapp.domain.model.RecurrenceType
import com.reminderapp.domain.model.recurrenceLabel
import com.reminderapp.ui.addreminder.AddReminderViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantInputBar(
    onDismiss: () -> Unit,
    onNavigateToAddReminder: () -> Unit,
    viewModel: AddReminderViewModel = hiltViewModel()
) {
    var inputText by remember { mutableStateOf("") }
    var parsedReminder by remember { mutableStateOf<Reminder?>(null) }
    var parseConfidence by remember { mutableFloatStateOf(1f) }
    var permissionDenied by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            keyboardController?.hide()
            viewModel.startVoiceInput()
        } else {
            permissionDenied = true
        }
    }

    fun requestOrStartMic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionDenied = false
            keyboardController?.hide()
            viewModel.startVoiceInput()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val speechState by viewModel.speechState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val readyForSpeech by viewModel.readyForSpeech.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.cancelVoiceInput() }
    }

    val submit: () -> Unit = {
        if (inputText.isNotBlank()) {
            viewModel.cancelVoiceInput()
            val result = viewModel.parseInput(inputText)
            parsedReminder = viewModel.buildReminderFromParse(result)
            parseConfidence = result.confidence
            keyboardController?.hide()
        }
    }

    LaunchedEffect(speechState) {
        when (val state = speechState) {
            is com.reminderapp.speech.SpeechState.PartialResult -> {
                if (isListening) inputText = state.text
            }
            is com.reminderapp.speech.SpeechState.Result -> {
                inputText = state.text
                if (state.text.isNotBlank() && parsedReminder == null) {
                    val result = viewModel.parseInput(state.text)
                    parsedReminder = viewModel.buildReminderFromParse(result)
                    parseConfidence = result.confidence
                    viewModel.resetSpeechState()
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val parsePreview = remember(inputText) {
        if (inputText.isBlank()) null else viewModel.parseInput(inputText)
    }
    val previewFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a") }
    val examplePhrases = listOf("in 10 minutes", "kal subah 9 baje", "tomorrow 5pm", "every Monday 8am")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp, end = 32.dp)
                ) {
                    when {
                        isListening -> {
                            VoiceLevelIndicator(level = audioLevel)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (readyForSpeech) "Speak now" else "Starting microphone…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        permissionDenied -> {
                            Icon(
                                Icons.Default.MicOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Microphone is off — tap to enable",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                    )
                                }
                            )
                        }
                        speechState is com.reminderapp.speech.SpeechState.Error -> {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = (speechState as com.reminderapp.speech.SpeechState.Error).message,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.ShortText,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Natural language",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
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
                                "e.g. Call mom tomorrow at 5pm",
                                style = MaterialTheme.typography.bodyLarge
                            ) 
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
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
                                        requestOrStartMic()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isListening) "Stop listening" else "Voice input",
                                    tint = if (isListening) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    Button(
                        onClick = { submit() },
                        enabled = inputText.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Parse")
                    }
                }

                if (!isListening) {
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        examplePhrases.forEach { phrase ->
                            AssistChip(
                                onClick = { inputText = phrase },
                                label = { Text(phrase, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                parsePreview?.let { preview ->
                    val whenText = preview.dateTime?.format(previewFormatter) ?: "time not found"
                    val repeatText = if (preview.recurrenceType != RecurrenceType.NONE) {
                        " · ${com.reminderapp.domain.model.recurrenceLabel(preview.recurrenceType, preview.recurrenceValue).lowercase()}"
                    } else {
                        ""
                    }
                    Text(
                        text = "${preview.title.ifBlank { "Reminder" }} · $whenText$repeatText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (preview.confidence < 0.8f) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 10.dp, end = 8.dp)
                    )
                }
            }
        }
    }

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        ConfirmationContent(
            reminder = reminder,
            confidence = confidence,
            onSave = onSave,
            onEdit = onEdit,
            onCancel = onCancel
        )
    }
}

@Composable
fun ConfirmationContent(
    reminder: Reminder,
    confidence: Float = 1f,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Save this reminder?",
                style = MaterialTheme.typography.headlineSmall
            )

            if (confidence < 0.8f) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Check details, I might have guessed some",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.large
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${reminder.reminderDateTime.format(dateFormatter)} · ${reminder.reminderDateTime.format(timeFormatter)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (reminder.notificationMode == com.reminderapp.domain.model.NotificationMode.ALARM) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Rings as an alarm",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (reminder.recurrenceType != RecurrenceType.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = reminder.recurrenceLabel(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit details")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            }
        }
}
