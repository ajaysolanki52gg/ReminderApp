package com.reminderapp.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminderapp.ui.addreminder.AddReminderViewModel
import com.reminderapp.speech.SpeechState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantOverlay(
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
    viewModel: AddReminderViewModel = hiltViewModel()
) {
    val speechState by viewModel.speechState.collectAsState()
    var sessionText by remember { mutableStateOf("") }
    var currentPartialText by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceInput()
    }

    // Start listening once on open
    LaunchedEffect(Unit) {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Handle incoming speech results
    LaunchedEffect(speechState) {
        when (val state = speechState) {
            is SpeechState.PartialResult -> {
                currentPartialText = state.text
            }
            is SpeechState.Result -> {
                // Append final result to session and auto-restart to keep listening
                if (state.text.isNotEmpty()) {
                    sessionText = if (sessionText.isEmpty()) state.text else "$sessionText ${state.text}"
                }
                currentPartialText = ""
                viewModel.startVoiceInput() 
            }
            is SpeechState.Idle -> {
                // If it goes idle (due to silence), restart if still on screen
                viewModel.startVoiceInput()
            }
            else -> {}
        }
    }

    val displayText = remember(sessionText, currentPartialText) {
        if (sessionText.isEmpty()) currentPartialText else "$sessionText $currentPartialText".trim()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, // Transparent to allow custom background
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(bottom = 48.dp)
        ) {
            // Animated Glowing Edge (Assistant vibe)
            GlowingBorder()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                )

                // Assistant Breathing Icon
                AssistantVisualizer(isListening = speechState is SpeechState.Listening || speechState is SpeechState.PartialResult)

                // Transcription Text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                ) {
                    Text(
                        text = if (displayText.isEmpty()) "How can I help?" else displayText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 36.sp
                        ),
                        textAlign = TextAlign.Center,
                        color = if (displayText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) 
                                else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Retry
                    FilledTonalIconButton(
                        onClick = { 
                            sessionText = ""
                            currentPartialText = ""
                            viewModel.startVoiceInput() 
                        },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }

                    // Confirm / Done
                    Button(
                        onClick = {
                            viewModel.stopVoiceInput()
                            if (displayText.isNotEmpty()) onResult(displayText) else onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    // Close
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantVisualizer(isListening: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "assistant")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Multi-layered animated glow
        if (isListening) {
            listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853)).forEachIndexed { index, color ->
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000 + (index * 500), easing = LinearEasing)
                    ),
                    label = "rotate"
                )
                
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(scale * (1f - (index * 0.1f)))
                        .blur(20.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f))
                )
            }
        }

        // Primary Mic Button
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun GlowingBorder() {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        Color(0xFF4285F4).copy(alpha = 0.5f),
                        Color(0xFF34A853).copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}
