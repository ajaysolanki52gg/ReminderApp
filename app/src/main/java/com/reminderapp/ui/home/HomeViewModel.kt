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
    val isMissedExpanded: Boolean = false
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
            repository.getUpcomingReminders(now, 3),
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

    fun markComplete(reminderId: Long) {
        viewModelScope.launch {
            repository.updateStatus(reminderId, ReminderStatus.COMPLETED)
            scheduler.cancel(reminderId)
            // Reschedule if recurring
            val reminder = repository.getReminderById(reminderId)
            reminder?.let { scheduler.rescheduleRecurring(it) }
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            scheduler.cancel(reminderId)
            repository.deleteReminder(reminderId)
        }
    }
}
