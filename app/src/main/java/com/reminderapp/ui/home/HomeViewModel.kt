package com.reminderapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.Reminder
import com.reminderapp.domain.model.ReminderStatus
import com.reminderapp.scheduler.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeUiState(
    val upcomingReminders: List<Reminder> = emptyList(),
    val completedReminders: List<Reminder> = emptyList(),
    val missedReminders: List<Reminder> = emptyList(),
    val completedCount: Int = 0,
    val missedCount: Int = 0,
    val isLoading: Boolean = true,
    val isCompletedExpanded: Boolean = false,
    val isMissedExpanded: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeReminders()
        markOverdueAsMissed()
    }

    private fun observeReminders() {
        val now = LocalDateTime.now()

        combine(
            repository.getUpcomingReminders(now, 50), // Increased limit from 3 to 50
            repository.getCompletedReminders(),
            repository.getMissedReminders(),
            repository.getCompletedCount(),
            repository.getMissedCount()
        ) { upcoming, completed, missed, completedCount, missedCount ->
            _uiState.update { state ->
                state.copy(
                    upcomingReminders = upcoming,
                    completedReminders = completed,
                    missedReminders = missed,
                    completedCount = completedCount,
                    missedCount = missedCount,
                    isLoading = false
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun markOverdueAsMissed() {
        viewModelScope.launch {
            val overdue = repository.getOverdueReminders(LocalDateTime.now())
            overdue.forEach { reminder ->
                repository.updateStatus(reminder.id, ReminderStatus.MISSED)
            }
        }
    }

    fun toggleCompletedExpanded() {
        _uiState.update { it.copy(isCompletedExpanded = !it.isCompletedExpanded) }
    }

    fun toggleMissedExpanded() {
        _uiState.update { it.copy(isMissedExpanded = !it.isMissedExpanded) }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val newSelected = if (state.selectedIds.contains(id)) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(
                selectedIds = newSelected,
                isSelectionMode = newSelected.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val idsToDelete = _uiState.value.selectedIds
            idsToDelete.forEach { id ->
                scheduler.cancel(id)
                repository.deleteReminder(id)
            }
            clearSelection()
        }
    }

    fun markComplete(reminderId: Long) {
        viewModelScope.launch {
            val reminder = repository.getReminderById(reminderId) ?: return@launch
            
            if (reminder.recurrenceType != com.reminderapp.domain.model.RecurrenceType.NONE) {
                // Recurring logic:
                if (reminder.status == ReminderStatus.MISSED) {
                    // 1. If it was already MISSED, just mark as completed and STOP.
                    // The future instance was already created when it became missed.
                    repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                } else {
                    // 2. If it's ACTIVE, update it to the NEXT occurrence.
                    val nextDate = scheduler.nextOccurrence(reminder)
                    if (nextDate != null) {
                        val updatedReminder = reminder.copy(
                            reminderDateTime = nextDate,
                            status = ReminderStatus.ACTIVE,
                            updatedAt = LocalDateTime.now()
                        )
                        repository.updateReminder(updatedReminder)
                        scheduler.schedule(updatedReminder)
                    }
                }
            } else {
                // One-time: Move to Completed
                repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                scheduler.cancel(reminderId)
            }
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            scheduler.cancel(reminderId)
            repository.deleteReminder(reminderId)
        }
    }
}
