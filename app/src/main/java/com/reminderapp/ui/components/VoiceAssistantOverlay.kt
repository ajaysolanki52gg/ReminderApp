package com.reminderapp.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminderapp.domain.model.Reminder
import com.reminderapp.speech.SpeechState
import com.reminderapp.ui.addreminder.AddReminderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantOverlay(
    onDismiss: () -> Unit,
    onNavigateToAddReminder: () -> Unit,
    viewModel: AddReminderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val speechState by viewModel.speechState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val readyForSpeech by viewModel.readyForSpeech.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()

    var sessionText by remember { mutableStateOf("") }
    var parsedReminder by remember { mutableStateOf<Reminder?>(null) }
    var parseConfidence by remember { mutableFloatStateOf(1f) }
    var permissionDenied by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            viewModel.startVoiceInput()
        } else {
            permissionDenied = true
        }
    }

    fun requestOrStartMic() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> {
                permissionDenied = false
                viewModel.startVoiceInput()
            }
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        requestOrStartMic()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.cancelVoiceInput() }
    }

    LaunchedEffect(speechState) {
        when (val state = speechState) {
            is SpeechState.PartialResult -> sessionText = state.text
            is SpeechState.Result -> {
                sessionText = state.text
                if (parsedReminder == null && state.text.isNotBlank()) {
                    val result = viewModel.parseInput(state.text)
                    parsedReminder = viewModel.buildReminderFromParse(result)
                    parseConfidence = result.confidence
                    viewModel.resetSpeechState()
                }
            }
            else -> {}
        }
    }

    val statusText = when {
        permissionDenied -> "Microphone permission is off"
        isListening && !readyForSpeech -> "Starting microphone…"
        isListening && sessionText.isBlank() -> "Speak now"
        isListening -> "Listening…"
        speechState is SpeechState.Error -> (speechState as SpeechState.Error).message
        sessionText.isNotBlank() -> "Is this right?"
        else -> "Say what to remember, and when"
    }

    val confirmed = parsedReminder

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.cancelVoiceInput()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        if (confirmed != null) {
            ConfirmationContent(
                reminder = confirmed,
                confidence = parseConfidence,
                onSave = {
                    viewModel.saveReminder(confirmed)
                    onDismiss()
                },
                onEdit = {
                    viewModel.stashForEdit(confirmed)
                    onNavigateToAddReminder()
                    onDismiss()
                },
                onCancel = { parsedReminder = null }
            )
        } else Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )

            AssistantVisualizer(
                isListening = isListening,
                audioLevel = audioLevel
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = if (speechState is SpeechState.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (sessionText.isBlank()) {
                        Text(
                            text = "Try saying",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "“Kal shaam 6 baje mummy ko call karna”\n“Remind me to pay rent every month on 1st”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    } else {
                        Text(
                            text = sessionText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (permissionDenied) {
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        val showRationale = activity != null &&
                            ActivityCompat.shouldShowRequestPermissionRationale(
                                activity,
                                Manifest.permission.RECORD_AUDIO
                            )
                        if (showRationale) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            context.openAppSettings()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Enable microphone")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            viewModel.cancelVoiceInput()
                            sessionText = ""
                            parsedReminder = null
                            requestOrStartMic()
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Try again")
                    }

                    Button(
                        onClick = {
                            if (isListening) {
                                viewModel.stopVoiceInput()
                            } else {
                                requestOrStartMic()
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isListening) "Done" else "Listen")
                    }

                    FilledTonalIconButton(
                        onClick = {
                            viewModel.cancelVoiceInput()
                            onDismiss()
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    )
}

@Composable
private fun AssistantVisualizer(
    isListening: Boolean,
    audioLevel: Float
) {
    val primary = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "assistant")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )
    // Smooth the raw mic level so the rings don't jitter.
    val level by animateFloatAsState(
        targetValue = if (isListening) audioLevel else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "level"
    )
    val coreScale by animateFloatAsState(
        targetValue = if (isListening) 1f + level * 0.12f else 0.92f,
        label = "core"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp)) {
        if (isListening) {
            // Two expanding rings, offset in phase, plus a level-driven halo.
            listOf(0f, 0.5f).forEach { offset ->
                val phase = (breathe + offset) % 1f
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .scale(1f + phase * 0.7f)
                        .alpha((1f - phase) * 0.35f)
                        .border(2.dp, primary, CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(1.1f + level * 0.45f)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.16f))
            )
        }
        Surface(
            modifier = Modifier
                .size(84.dp)
                .scale(coreScale),
            shape = CircleShape,
            color = if (isListening) primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = if (isListening) 6.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isListening) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
