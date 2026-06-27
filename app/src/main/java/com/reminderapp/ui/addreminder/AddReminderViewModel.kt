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
    private val speechManager: SpeechRecognitionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddReminderUiState())
    val uiState: StateFlow<AddReminderUiState> = _uiState.asStateFlow()

    val speechState: StateFlow<SpeechState> = speechManager.state

    init {
        viewModelScope.launch {
            settingsRepository.settings.first().let { settings ->
                _uiState.update { it.copy(notificationMode = settings.defaultNotificationMode) }
            }
        }
    }

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
            ReminderType.BIWEEKLY -> RecurrenceType.BIWEEKLY
            ReminderType.MONTHLY -> RecurrenceType.MONTHLY
            ReminderType.YEARLY -> RecurrenceType.YEARLY
        }
        _uiState.update { it.copy(reminderType = type, recurrenceType = recurrenceType) }
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

    fun startVoiceInput() = speechManager.startListening()
    fun stopVoiceInput() = speechManager.stopListening()
    fun resetSpeechState() = speechManager.resetState()

    fun restartVoiceInput() {
        speechManager.stopListening()
        speechManager.startListening()
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    fun saveReminder(prefilled: Reminder? = null) {
        val state = _uiState.value
        val reminder = prefilled ?: Reminder(
            id = state.editingReminderId ?: 0,
            title = state.title.trim(),
            description = state.description.trim(),
            reminderDateTime = LocalDateTime.of(state.selectedDate, state.selectedTime),
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
        speechManager.stopListening()
    }
}
