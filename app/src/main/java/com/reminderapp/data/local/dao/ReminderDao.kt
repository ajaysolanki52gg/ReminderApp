package com.reminderapp.data.local.dao

import androidx.room.*
import com.reminderapp.data.local.entity.ReminderEntity
import com.reminderapp.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ReminderDao {

    // ─── Insert / Update / Delete ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ─── Queries ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<ReminderEntity?>

    @Query("""
        SELECT * FROM reminders 
        WHERE status = 'ACTIVE' 
        AND reminderDateTime >= :now
        ORDER BY reminderDateTime ASC
        LIMIT :limit
    """)
    fun getUpcoming(now: LocalDateTime, limit: Int = 3): Flow<List<ReminderEntity>>

    @Query("""
        SELECT * FROM reminders 
        WHERE status = 'ACTIVE' 
        AND reminderDateTime >= :now
        ORDER BY reminderDateTime ASC
    """)
    fun getAllUpcoming(now: LocalDateTime): Flow<List<ReminderEntity>>

    @Query("""
        SELECT * FROM reminders 
        WHERE status = 'COMPLETED'
        ORDER BY updatedAt DESC
    """)
    fun getCompleted(): Flow<List<ReminderEntity>>

    @Query("""
        SELECT * FROM reminders 
        WHERE status = 'MISSED'
        ORDER BY reminderDateTime DESC
    """)
    fun getMissed(): Flow<List<ReminderEntity>>

    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'COMPLETED'")
    fun getCompletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'MISSED'")
    fun getMissedCount(): Flow<Int>

    @Query("""
        SELECT * FROM reminders 
        WHERE reminderDateTime < :now 
        AND status = 'ACTIVE'
    """)
    suspend fun getOverdueReminders(now: LocalDateTime): List<ReminderEntity>

    @Query("""
        UPDATE reminders SET status = :status, updatedAt = :updatedAt WHERE id = :id
    """)
    suspend fun updateStatus(id: Long, status: ReminderStatus, updatedAt: LocalDateTime = LocalDateTime.now())

    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' ORDER BY reminderDateTime ASC")
    suspend fun getAllActiveReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    suspend fun getAllReminders(): List<ReminderEntity>
}
