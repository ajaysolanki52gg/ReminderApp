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

    LaunchedEffect(Unit) {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(speechState) {
        when (val state = speechState) {
            is SpeechState.PartialResult -> {
                currentPartialText = state.text
            }
            is SpeechState.Result -> {
                if (state.text.isNotEmpty()) {
                    sessionText = if (sessionText.isEmpty()) state.text else "$sessionText ${state.text}"
                }
                currentPartialText = ""
                viewModel.startVoiceInput()
            }
            is SpeechState.Idle -> {
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
        containerColor = Color.Transparent,
        scrimColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp))
                .background(
                    Brush.verticalGradient(colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ))
                )
                .padding(bottom = 56.dp)
        ) {
            GlowingBreatheBorder()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                )

                AssistantVisualizer(
                    isListening = speechState is SpeechState.Listening || speechState is SpeechState.PartialResult
                )

                GradientTextCard(
                    text = if (displayText.isEmpty()) "How can I help?" else displayText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { 
                            sessionText = ""
                            currentPartialText = ""
                            viewModel.startVoiceInput()
                        },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }

                    Button(
                        onClick = {
                            viewModel.stopVoiceInput()
                            if (displayText.isNotEmpty()) onResult(displayText) else onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(14.dp))
                        Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun GlowingBreatheBorder() {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe_border")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(
                Brush.horizontalGradient(colors = listOf<Color>(
                    Color.Transparent,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    Color.Transparent
                ))
            )
    )
}

@Composable
fun AssistantVisualizer(isListening: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "assistant")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            val secondary = MaterialTheme.colorScheme.secondary
            val primary = MaterialTheme.colorScheme.primary
            listOf(
                secondary.copy(alpha = 0.2f),
                primary.copy(alpha = 0.18f),
                secondary.copy(alpha = 0.4f)
            ).forEachIndexed { index, color ->
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4500 + (index * 1000), easing = LinearEasing)
                    ),
                    label = "rotate"
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale * (1f - (index * 0.12f)))
                        .blur(24.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                tonalElevation = 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GradientTextCard(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = color,
            modifier = Modifier.padding(16.dp)
        )
    }
}
