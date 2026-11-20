package com.reminderapp.ui.addreminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.data.repository.SettingsRepository
import com.reminderapp.domain.model.*
import com.reminderapp.domain.parser.NaturalLanguageParser
import com.reminderapp.scheduler.ReminderScheduler
import com.reminderapp.speech.SpeechRecognitionManager
import com.reminderapp.speech.SpeechState
import com.reminderapp.util.PendingReminderHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class AddReminderUiState(
    val title: String = "",
    val description: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedTime: LocalTime = LocalTime.now().plusMinutes(5).withSecond(0).withNano(0),
    val reminderType: ReminderType = ReminderType.ONE_TIME,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceValue: String = "",
    val notificationMode: NotificationMode = NotificationMode.NOTIFICATION,
    val isEditMode: Boolean = false,
    val editingReminderId: Long? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddReminderViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val parser: NaturalLanguageParser,
    private val speechManager: SpeechRecognitionManager,
    private val pendingReminderHolder: PendingReminderHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddReminderUiState())
    val uiState: StateFlow<AddReminderUiState> = _uiState.asStateFlow()

    val speechState: StateFlow<SpeechState> = speechManager.state
    val isListening: StateFlow<Boolean> = speechManager.isListening
    val readyForSpeech: StateFlow<Boolean> = speechManager.readyForSpeech
    val audioLevel: StateFlow<Float> = speechManager.audioLevel

    private var speechLanguageTag: String = ""
    private var voiceStartGeneration = 0

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            speechLanguageTag = settings.speechLanguage
            _uiState.update { it.copy(notificationMode = settings.defaultNotificationMode) }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { speechLanguageTag = it.speechLanguage }
        }
        // Prefill from a reminder parsed on the Home screen's assistant bar, if the user tapped
        // "Edit" to bring it here rather than saving it directly.
        pendingReminderHolder.consume()?.let { prefillFromParsed(it) }
    }

    /** Stashes a not-yet-saved parsed [Reminder] for the next AddReminderViewModel instance to pick up. */
    fun stashForEdit(reminder: Reminder) = pendingReminderHolder.set(reminder)

    fun loadReminder(reminderId: Long) {
        viewModelScope.launch {
            val reminder = repository.getReminderById(reminderId) ?: return@launch
            _uiState.update {
                it.copy(
                    title = reminder.title,
                    description = reminder.description,
                    selectedDate = reminder.reminderDateTime.toLocalDate(),
                    selectedTime = reminder.reminderDateTime.toLocalTime(),
                    reminderType = reminder.reminderType,
                    recurrenceType = reminder.recurrenceType,
                    recurrenceValue = reminder.recurrenceValue,
                    notificationMode = reminder.notificationMode,
                    isEditMode = true,
                    editingReminderId = reminderId
                )
            }
        }
    }

    // ─── Field Updaters ───────────────────────────────────────────────────────

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun updateDate(value: LocalDate) = _uiState.update { it.copy(selectedDate = value) }
    fun updateTime(value: LocalTime) = _uiState.update { it.copy(selectedTime = value) }
    fun updateNotificationMode(value: NotificationMode) = _uiState.update { it.copy(notificationMode = value) }

    fun updateReminderType(type: ReminderType) {
        val recurrenceType = when (type) {
            ReminderType.ONE_TIME -> RecurrenceType.NONE
            ReminderType.DAILY -> RecurrenceType.DAILY
            ReminderType.WEEKLY -> RecurrenceType.WEEKLY
            ReminderType.MONTHLY -> RecurrenceType.MONTHLY
            ReminderType.YEARLY -> RecurrenceType.YEARLY
        }
        _uiState.update { state ->
            val date = state.selectedDate
            val value = when (type) {
                ReminderType.ONE_TIME, ReminderType.DAILY -> ""
                // Keep already-chosen days; otherwise start with the selected date's weekday.
                ReminderType.WEEKLY -> state.recurrenceValue
                    .takeIf { state.recurrenceType == RecurrenceType.WEEKLY && WeekDays.parse(it).isNotEmpty() }
                    ?: date.dayOfWeek.name
                ReminderType.MONTHLY -> date.dayOfMonth.toString()
                ReminderType.YEARLY -> "${date.month.name}_${date.dayOfMonth}"
            }
            state.copy(reminderType = type, recurrenceType = recurrenceType, recurrenceValue = value)
        }
    }

    /** Weekly reminders: turn one day on/off. At least one day always stays selected. */
    fun toggleWeekDay(day: java.time.DayOfWeek) = _uiState.update { state ->
        val current = WeekDays.parse(state.recurrenceValue)
        val updated = if (day in current) current - day else current + day
        if (updated.isEmpty()) state else state.copy(recurrenceValue = WeekDays.format(updated))
    }

    fun setWeekDays(days: Set<java.time.DayOfWeek>) = _uiState.update {
        if (days.isEmpty()) it else it.copy(recurrenceValue = WeekDays.format(days))
    }

    // ─── NLP ──────────────────────────────────────────────────────────────────

    fun parseInput(text: String) = parser.parse(text)

    fun buildReminderFromParse(result: com.reminderapp.domain.model.ReminderParseResult): Reminder {
        val now = LocalDateTime.now()
        return Reminder(
            title = result.title.ifEmpty { result.rawInput },
            reminderDateTime = result.dateTime ?: now.plusHours(1),
            reminderType = result.reminderType,
            recurrenceType = result.recurrenceType,
            recurrenceValue = result.recurrenceValue,
            notificationMode = _uiState.value.notificationMode
        )
    }

    fun prefillFromParsed(reminder: Reminder) {
        _uiState.update {
            it.copy(
                title = reminder.title,
                selectedDate = reminder.reminderDateTime.toLocalDate(),
                selectedTime = reminder.reminderDateTime.toLocalTime(),
                reminderType = reminder.reminderType,
                recurrenceType = reminder.recurrenceType,
                recurrenceValue = reminder.recurrenceValue,
                notificationMode = reminder.notificationMode
            )
        }
    }

    // ─── Voice ────────────────────────────────────────────────────────────────

    fun startVoiceInput() {
        val generation = ++voiceStartGeneration
        viewModelScope.launch {
            val tag = settingsRepository.settings.first().speechLanguage
            if (generation != voiceStartGeneration) return@launch
            speechLanguageTag = tag
            speechManager.startListening(tag)
        }
    }

    /** "Done": finish listening and deliver the final transcription as a result. */
    fun stopVoiceInput() {
        voiceStartGeneration++
        speechManager.stopListening()
    }

    /** Abandon voice input without producing a result (sheet closed / screen left). */
    fun cancelVoiceInput() {
        voiceStartGeneration++
        speechManager.resetState()
    }

    fun resetSpeechState() {
        voiceStartGeneration++
        speechManager.resetState()
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    fun saveReminder(prefilled: Reminder? = null) {
        val state = _uiState.value
        val chosenDateTime = LocalDateTime.of(state.selectedDate, state.selectedTime)
        // Weekly on chosen days: start on the first selected day from the chosen date onwards
        // (e.g. "Weekdays" picked on a Saturday starts on Monday).
        val firstDateTime = if (state.recurrenceType == RecurrenceType.WEEKLY) {
            WeekDays.firstOccurrence(chosenDateTime, WeekDays.parse(state.recurrenceValue), LocalDateTime.now())
        } else {
            chosenDateTime
        }
        val reminder = prefilled ?: Reminder(
            id = state.editingReminderId ?: 0,
            title = state.title.trim(),
            description = state.description.trim(),
            reminderDateTime = firstDateTime,
            reminderType = state.reminderType,
            recurrenceType = state.recurrenceType,
            recurrenceValue = state.recurrenceValue,
            notificationMode = state.notificationMode
        )

        if (reminder.title.isBlank()) {
            _uiState.update { it.copy(error = "Title cannot be empty") }
            return
        }

        if (reminder.reminderDateTime.isBefore(LocalDateTime.now())) {
            _uiState.update { it.copy(error = "Cannot set a reminder in the past") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val savedId = if (state.isEditMode && state.editingReminderId != null) {
                    scheduler.cancel(state.editingReminderId)
                    repository.updateReminder(reminder.copy(id = state.editingReminderId))
                    state.editingReminderId
                } else {
                    repository.insertReminder(reminder)
                }
                scheduler.schedule(reminder.copy(id = savedId))
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        cancelVoiceInput()
    }
}
