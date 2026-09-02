package com.reminderapp.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    // True while the user wants the mic open. Used to auto-resume recognition when the system
    // ends a session after a short silence, instead of cutting the user off mid-sentence.
    private var isListeningRequested = false

    // Finalized speech accumulated across auto-restarts, so a pause doesn't wipe out text that
    // was already recognized.
    private var accumulatedText = ""

    // accumulatedText plus whatever the current in-progress utterance has recognized so far.
    // stopListening() must prefer this over accumulatedText alone: onResults() for the current
    // utterance may not have fired yet (the recognizer can hold a session open for several
    // seconds after speech ends), so accumulatedText can lag behind what's already on screen -
    // finalizing from it would silently revert the input field to stale text.
    private var latestMergedText = ""

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state

    // Separate from [state] on purpose: [state] changes type (Listening -> PartialResult) as soon
    // as any words are recognized, which was previously used (incorrectly) as the "is recording"
    // signal and made the mic button flicker back to its "start" icon while still recording -
    // tapping it again then restarted the recognizer and discarded whatever was being said.
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    // Normalized mic input level (0f-1f) while listening, for a live waveform/level indicator.
    // SpeechRecognizer reports RMS dB roughly in the range -2..10; anything outside that is clamped.
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechState.Error("Speech recognition not available on this device")
            return
        }

        // Already listening: ignore instead of tearing down and recreating the recognizer, which
        // was interrupting the user whenever the mic button was tapped again mid-sentence.
        if (isListeningRequested) return

        isListeningRequested = true
        accumulatedText = ""
        latestMergedText = ""
        _isListening.value = true
        createRecognizerIfNeeded()
        speechRecognizer?.startListening(buildRecognizerIntent())
    }

    private fun createRecognizerIfNeeded() {
        if (speechRecognizer != null) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = SpeechState.Listening
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _audioLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // A brief silence, not a real failure: resume listening automatically
                            // so a mid-sentence pause doesn't end the session.
                            if (isListeningRequested) {
                                speechRecognizer?.startListening(buildRecognizerIntent())
                            } else {
                                _state.value = SpeechState.Idle
                            }
                        }
                        else -> {
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
                                else -> "Unknown error"
                            }
                            _state.value = SpeechState.Error(message)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        accumulatedText = mergeText(accumulatedText, text)
                    }
                    latestMergedText = accumulatedText
                    if (isListeningRequested) {
                        // Keep the mic open: a finished recognition pass just means the recognizer
                        // detected a pause, not that the user is done speaking.
                        _state.value = SpeechState.PartialResult(accumulatedText)
                        speechRecognizer?.startListening(buildRecognizerIntent())
                    } else {
                        _state.value = if (accumulatedText.isNotBlank()) {
                            SpeechState.Result(accumulatedText)
                        } else {
                            SpeechState.Idle
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        latestMergedText = mergeText(accumulatedText, text)
                        _state.value = SpeechState.PartialResult(latestMergedText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun mergeText(base: String, addition: String): String =
        listOf(base, addition).filter { it.isNotBlank() }.joinToString(" ")

    private fun buildRecognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // The defaults (~1-2s of silence) were ending recognition while the user was still
        // mid-sentence. These give much more room to pause before the recognizer finalizes.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
        // This is a floor on total recording time, not extra silence - the recognizer won't
        // finalize before it elapses even if speech + silence finished well before. It was
        // previously 15000ms, which made a 3-word command sit for up to 15s before the mic
        // released; 3000ms is enough to avoid clipping a very short utterance without adding
        // that lag to typical commands.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
    }

    fun stopListening() {
        isListeningRequested = false
        _isListening.value = false
        _audioLevel.value = 0f
        speechRecognizer?.setRecognitionListener(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        val finalText = latestMergedText.ifBlank { accumulatedText }
        _state.value = if (finalText.isNotBlank()) {
            SpeechState.Result(finalText)
        } else {
            SpeechState.Idle
        }
    }

    fun resetState() {
        accumulatedText = ""
        latestMergedText = ""
        _state.value = SpeechState.Idle
    }
}

