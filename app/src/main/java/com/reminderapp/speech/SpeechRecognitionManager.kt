package com.reminderapp.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    data class PartialResult(val text: String) : SpeechState()
    data class Result(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}

@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSessionId = 0
    private var isListeningRequested = false
    private var accumulatedText = ""
    private var latestMergedText = ""
    
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechState.Error("Speech recognition not available on this device")
            return
        }

        if (isListeningRequested) return

        isListeningRequested = true
        currentSessionId++
        val sessionId = currentSessionId
        
        accumulatedText = ""
        latestMergedText = ""
        _isListening.value = true
        
        mainHandler.post {
            createAndStartInternal(sessionId)
        }
    }

    private fun createAndStartInternal(sessionId: Int) {
        if (sessionId != currentSessionId || !isListeningRequested) return
        
        destroyRecognizer()
        
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        
        recognizer.setRecognitionListener(object : RecognitionListener {
            private fun isSessionValid(): Boolean = sessionId == currentSessionId

            override fun onReadyForSpeech(params: Bundle?) {
                if (!isSessionValid()) return
                _state.value = SpeechState.Listening
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (!isSessionValid()) return
                _audioLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (!isSessionValid()) return
                _audioLevel.value = 0f
            }

            override fun onError(error: Int) {
                if (!isSessionValid()) return
                
                isListeningRequested = false
                _isListening.value = false
                _audioLevel.value = 0f
                
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Speech recognition error ($error)"
                }
                _state.value = SpeechState.Error(message)
                destroyRecognizer()
            }

            override fun onResults(results: Bundle?) {
                if (!isSessionValid()) return
                
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                
                if (!text.isNullOrBlank()) {
                    accumulatedText = updateAccumulatedText(accumulatedText, text)
                }
                latestMergedText = accumulatedText

                isListeningRequested = false
                _isListening.value = false
                _audioLevel.value = 0f
                
                _state.value = if (accumulatedText.isNotBlank()) {
                    SpeechState.Result(accumulatedText)
                } else {
                    SpeechState.Idle
                }
                
                destroyRecognizer()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isSessionValid()) return
                
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    latestMergedText = updateAccumulatedText(accumulatedText, text)
                    _state.value = SpeechState.PartialResult(latestMergedText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        try {
            recognizer.startListening(buildRecognizerIntent())
        } catch (e: Exception) {
            if (sessionId == currentSessionId) {
                isListeningRequested = false
                _isListening.value = false
                _state.value = SpeechState.Error("Failed to start recognizer")
                destroyRecognizer()
            }
        }
    }

    /**
     * Intelligently updates the accumulated text.
     * Prevents duplication if the new 'addition' already contains the 'base'.
     */
    private fun updateAccumulatedText(base: String, addition: String): String {
        val cleanBase = base.trim()
        val cleanAddition = addition.trim()
        
        return when {
            cleanBase.isEmpty() -> cleanAddition
            cleanAddition.isEmpty() -> cleanBase
            // If the addition contains the base, it's likely a full sequence update
            cleanAddition.lowercase().startsWith(cleanBase.lowercase()) -> cleanAddition
            // Otherwise, append with a space
            else -> "$cleanBase $cleanAddition"
        }
    }

    private fun buildRecognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        
        // Use standard timeouts for single utterance
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
    }

    fun stopListening() {
        currentSessionId++
        isListeningRequested = false
        _isListening.value = false
        _audioLevel.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        
        val finalText = latestMergedText.ifBlank { accumulatedText }
        if (_state.value !is SpeechState.Result && _state.value !is SpeechState.Error) {
            _state.value = if (finalText.isNotBlank()) {
                SpeechState.Result(finalText)
            } else {
                SpeechState.Idle
            }
        }
        
        mainHandler.post {
            destroyRecognizer()
        }
    }

    private fun destroyRecognizer() {
        speechRecognizer?.setRecognitionListener(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun resetState() {
        currentSessionId++
        isListeningRequested = false
        _isListening.value = false
        accumulatedText = ""
        latestMergedText = ""
        _state.value = SpeechState.Idle
        _audioLevel.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            destroyRecognizer()
        }
    }
}
