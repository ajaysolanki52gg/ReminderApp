package com.reminderapp.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
    data class Error(val message: String, val recoverable: Boolean = true) : SpeechState()
}

object SpeechLanguages {
    const val DEVICE_DEFAULT = ""
    val options = listOf(
        DEVICE_DEFAULT to "Device default",
        "en-IN" to "English (India) · best for Hinglish",
        "en-US" to "English (US)",
        "hi-IN" to "Hindi"
    )
}

/**
 * Wraps Android's [SpeechRecognizer].
 *
 * On Android 12+ it prefers the on-device recognizer (offline, private, no network round trip)
 * and transparently falls back to the default recognition service if the on-device one can't
 * handle the requested language or isn't installed.
 *
 * All recognizer calls happen on the main thread, as the platform requires.
 */
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
    private var retryCount = 0
    private var languageTag: String = SpeechLanguages.DEVICE_DEFAULT
    private var hasCuedReady = false

    /** Set once the on-device recognizer has failed for a language, so we stop trying it. */
    private var onDeviceDisabledForTag: String? = null
    private var usingOnDevice = false

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _readyForSpeech = MutableStateFlow(false)
    val readyForSpeech: StateFlow<Boolean> = _readyForSpeech

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    fun startListening(languageTag: String = SpeechLanguages.DEVICE_DEFAULT) {
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !isOnDeviceAvailable()) {
            _state.value = SpeechState.Error(
                "Voice input isn't available on this device",
                recoverable = false
            )
            return
        }

        if (isListeningRequested) return

        this.languageTag = languageTag
        isListeningRequested = true
        currentSessionId++
        val sessionId = currentSessionId
        retryCount = 0
        hasCuedReady = false
        accumulatedText = ""
        latestMergedText = ""
        _isListening.value = true
        _readyForSpeech.value = false
        _state.value = SpeechState.Listening

        mainHandler.post { createAndStartInternal(sessionId) }
    }

    private fun isOnDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private fun createRecognizer(): SpeechRecognizer {
        val tryOnDevice = isOnDeviceAvailable() && onDeviceDisabledForTag != languageTag
        if (tryOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                usingOnDevice = true
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (_: Exception) {
                onDeviceDisabledForTag = languageTag
            }
        }
        usingOnDevice = false
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun createAndStartInternal(sessionId: Int) {
        if (sessionId != currentSessionId || !isListeningRequested) return

        destroyRecognizer()

        val recognizer = try {
            createRecognizer()
        } catch (e: Exception) {
            failSession("Couldn't start the microphone. Tap to try again.")
            return
        }
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            private fun isSessionValid(): Boolean = sessionId == currentSessionId

            override fun onReadyForSpeech(params: Bundle?) {
                if (!isSessionValid()) return
                _readyForSpeech.value = true
                // Only buzz once per session, not on every silent internal restart.
                if (!hasCuedReady) {
                    hasCuedReady = true
                    cueReady()
                }
                if (_state.value !is SpeechState.PartialResult) {
                    _state.value = SpeechState.Listening
                }
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

                _audioLevel.value = 0f
                _readyForSpeech.value = false

                val heard = latestMergedText.ifBlank { accumulatedText }.trim()

                // The on-device engine doesn't have this language (or its pack isn't
                // downloaded): silently switch to the regular recognizer and carry on.
                if (usingOnDevice && heard.isEmpty() && error in ON_DEVICE_FALLBACK_ERRORS) {
                    onDeviceDisabledForTag = languageTag
                    mainHandler.post { createAndStartInternal(sessionId) }
                    return
                }

                // Many recognizers report NO_MATCH / SPEECH_TIMEOUT right after the user
                // stops talking even though partial results were already delivered. If we
                // heard something, that IS the result - don't throw it away.
                if (heard.isNotEmpty() && error in END_OF_UTTERANCE_ERRORS) {
                    finishWith(heard)
                    return
                }

                // Nothing heard yet: quietly restart a couple of times so a slow start or a
                // brief pause doesn't kill the session.
                val recoverable = error in RECOVERABLE_ERRORS
                if (recoverable && retryCount < MAX_RETRIES && isListeningRequested) {
                    retryCount++
                    mainHandler.postDelayed({
                        if (sessionId == currentSessionId && isListeningRequested) {
                            createAndStartInternal(sessionId)
                        }
                    }, RETRY_DELAY_MS)
                    return
                }

                if (heard.isNotEmpty()) {
                    finishWith(heard)
                } else {
                    failSession(messageFor(error), recoverable)
                }
            }

            override fun onResults(results: Bundle?) {
                if (!isSessionValid()) return

                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                if (!text.isNullOrBlank()) {
                    accumulatedText = mergeText(accumulatedText, text)
                } else if (latestMergedText.isNotBlank()) {
                    accumulatedText = latestMergedText
                }

                if (accumulatedText.isNotBlank()) {
                    finishWith(accumulatedText)
                } else {
                    failSession("I didn't catch that. Tap the mic to try again.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isSessionValid()) return

                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    latestMergedText = mergeText(accumulatedText, text)
                    _state.value = SpeechState.PartialResult(latestMergedText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(buildRecognizerIntent())
        } catch (e: Exception) {
            if (sessionId == currentSessionId) {
                failSession("Couldn't start the microphone. Tap to try again.")
            }
        }
    }

    private fun finishWith(text: String) {
        currentSessionId++
        isListeningRequested = false
        _isListening.value = false
        _readyForSpeech.value = false
        _audioLevel.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        _state.value = SpeechState.Result(text.trim())
        destroyRecognizer()
    }

    private fun failSession(message: String, recoverable: Boolean = true) {
        currentSessionId++
        isListeningRequested = false
        _isListening.value = false
        _readyForSpeech.value = false
        _audioLevel.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        _state.value = SpeechState.Error(message, recoverable)
        destroyRecognizer()
    }

    private fun mergeText(base: String, addition: String): String {
        val cleanBase = base.trim()
        val cleanAddition = addition.trim()

        return when {
            cleanBase.isEmpty() -> cleanAddition
            cleanAddition.isEmpty() -> cleanBase
            cleanAddition.lowercase().startsWith(cleanBase.lowercase()) -> cleanAddition
            else -> "$cleanBase $cleanAddition"
        }
    }

    private fun buildRecognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        val locale = resolveLocale()
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
    }

    private fun resolveLocale(): Locale {
        val tag = languageTag.trim()
        return if (tag.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(tag)
    }

    private fun cueReady() {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Couldn't hear the microphone. Try again."
        SpeechRecognizer.ERROR_CLIENT -> "Voice input got interrupted. Tap the mic to try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off. Enable it in Settings."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "No offline voice pack for this language. Connect to the internet or change the language in Settings."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice input is busy. Tap to try again."
        SpeechRecognizer.ERROR_SERVER -> "The speech service had a problem. Try again."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "I didn't catch that. Tap the mic to try again."
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
            "This voice language isn't available. Pick another one in Settings."
        else -> "Voice input failed (code $error). Tap to try again."
    }

    /**
     * "Done" pressed: ask the recognizer to finish so it delivers its *final* transcription
     * (usually more accurate than the last partial). If it doesn't answer quickly, fall back
     * to whatever partial text we already have.
     */
    fun stopListening() {
        if (!isListeningRequested) return
        val sessionId = currentSessionId
        isListeningRequested = false
        mainHandler.removeCallbacksAndMessages(null)

        val recognizer = speechRecognizer
        if (recognizer == null) {
            finishOrIdle()
            return
        }

        mainHandler.post {
            try {
                recognizer.stopListening()
            } catch (_: Exception) {
            }
        }
        mainHandler.postDelayed({
            if (sessionId == currentSessionId) finishOrIdle()
        }, STOP_GRACE_MS)
    }

    private fun finishOrIdle() {
        val text = latestMergedText.ifBlank { accumulatedText }
        if (text.isNotBlank()) {
            finishWith(text)
        } else {
            currentSessionId++
            _isListening.value = false
            _readyForSpeech.value = false
            _audioLevel.value = 0f
            _state.value = SpeechState.Idle
            destroyRecognizer()
        }
    }

    /** Hard stop: abandon the session without producing a result (sheet closed, etc.). */
    fun cancel() {
        currentSessionId++
        isListeningRequested = false
        _isListening.value = false
        _readyForSpeech.value = false
        _audioLevel.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { destroyRecognizer() }
    }

    private fun destroyRecognizer() {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null
        try {
            recognizer.setRecognitionListener(null)
            recognizer.destroy()
        } catch (_: Exception) {
        }
    }

    fun resetState() {
        cancel()
        retryCount = 0
        accumulatedText = ""
        latestMergedText = ""
        _state.value = SpeechState.Idle
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 350L
        private const val STOP_GRACE_MS = 1500L

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31).
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        private val END_OF_UTTERANCE_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT
        )

        private val RECOVERABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        )

        /** Errors from the on-device engine that mean "use the regular recognizer instead". */
        private val ON_DEVICE_FALLBACK_ERRORS = setOf(
            ERROR_LANGUAGE_NOT_SUPPORTED,
            ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_NETWORK
        )
    }
}
