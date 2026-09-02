package com.reminderapp.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import com.reminderapp.data.repository.ReminderRepository
import com.reminderapp.domain.model.Reminder
import com.reminderapp.domain.model.ReminderStatus
import com.reminderapp.scheduler.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler
) {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ ->
            com.google.gson.JsonPrimitive(src.format(formatter))
        })
        .registerTypeAdapter(LocalDateTime::class.java, JsonDeserializer<LocalDateTime> { json, _, _ ->
            LocalDateTime.parse(json.asString, formatter)
        })
        .setPrettyPrinting()
        .create()

    suspend fun exportBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val reminders = repository.getAllReminders()
            val json = gson.toJson(reminders)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                }
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            } ?: return@withContext Result.failure(Exception("Could not read file"))

            if (json.isBlank()) {
                return@withContext Result.failure(Exception("Backup file is empty"))
            }

            val type = object : com.google.gson.reflect.TypeToken<List<Reminder>>() {}.type
            val importedReminders: List<Reminder> = gson.fromJson(json, type)
                ?: return@withContext Result.failure(Exception("Invalid backup format"))

            // 1. Cancel all existing notifications/alarms before clearing the DB
            scheduler.cancelAll()

            // 2. Clear existing database
            repository.deleteAllReminders()

            // 3. Insert imported reminders (reset IDs to 0 to ensure new unique IDs)
            val remindersToInsert = importedReminders.map { it.copy(id = 0) }
            val newIds = repository.insertReminders(remindersToInsert)

            // 4. Schedule active reminders with their new IDs
            remindersToInsert.zip(newIds).forEach { (reminder, newId) ->
                if (reminder.status == ReminderStatus.ACTIVE) {
                    scheduler.schedule(reminder.copy(id = newId))
                }
            }

            Result.success(importedReminders.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
