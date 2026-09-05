# Fix SpeechRecognizer Lifecycle and Overlapping Sessions

The current implementation of `SpeechRecognitionManager` uses a `scheduleRestart()` mechanism that automatically restarts recognition upon receiving results or recoverable errors. This leads to overlapping sessions and multiple `SpeechRecognizer` instances. We will transition to a single-utterance recognition model with a robust lifecycle managed by a session ID.

## User Review Required

> [!IMPORTANT]
> The voice input will now stop automatically after a single result is returned or an error occurs. The user will need to manually restart voice input if they wish to try again. This aligns with the "single-utterance" requirement.

## Proposed Changes

### Speech Recognition

#### [MODIFY] [SpeechRecognitionManager.kt](file:///C:/Users/user/AndroidStudioProjects/ReminderApp/app/src/main/java/com/reminderapp/speech/SpeechRecognitionManager.kt)

- Introduce `currentSessionId` to track the active recognition session.
- Increment `currentSessionId` in `startListening()` and `stopListening()` to invalidate previous sessions.
- Update `RecognitionListener` to check the `sessionId` for all callbacks.
- Remove `scheduleRestart()` and `isRestarting` logic.
- Ensure all `SpeechRecognizer` lifecycle methods (create, start, stop, destroy) are executed on the main thread.
- Update `onResults()` to publish the final result and set `isListeningRequested = false`.
- Update `onError()` to report the error and set `isListeningRequested = false`, without auto-restarting.

## Verification Plan

### Automated Tests
- Run existing unit tests (if any) to ensure no regressions in NLP parsing or UI state management.
- `gradlew :app:assembleDebug` to verify compilation.

### Manual Verification
1. Open the Assistant Input Bar.
2. Start voice input.
3. Speak a reminder.
4. Verify that recognition stops automatically after the result is received.
5. Verify that no overlapping recognizers are created (no multiple "Listening" logs or unexpected state jumps).
6. Test with silence/timeout and verify that the session ends gracefully without auto-restart.
7. Test the "Retry" button in `VoiceAssistantOverlay` to ensure manual restart still works.
