package com.example.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userTranscript: String,
    val assistantResponse: String,
    val actionCategory: String,
    val parametersJson: String = "{}",
    val languageCode: String = "en-US",
    val wasOffline: Boolean = false,
    val status: String = "SUCCESS", // SUCCESS, REQUIRES_CONFIRMATION, PERMISSION_NEEDED, ERROR, CANCELLED
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val scheduledDate: String,
    val scheduledTime: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface JarvisDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllCommandLogs(): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 8): List<CommandLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommandLog(log: CommandLogEntity): Long

    @Query("DELETE FROM command_logs")
    suspend fun clearAllLogs()

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun setReminderCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun clearAllReminders()
}

@Database(
    entities = [CommandLogEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_assistant_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class JarvisRepository(private val dao: JarvisDao) {
    val commandLogs: Flow<List<CommandLogEntity>> = dao.getAllCommandLogs()
    val reminders: Flow<List<ReminderEntity>> = dao.getAllReminders()

    suspend fun logInteraction(
        userTranscript: String,
        assistantResponse: String,
        actionCategory: String,
        parametersJson: String = "{}",
        languageCode: String = "en-US",
        wasOffline: Boolean = false,
        status: String = "SUCCESS"
    ): Long {
        return dao.insertCommandLog(
            CommandLogEntity(
                userTranscript = userTranscript,
                assistantResponse = assistantResponse,
                actionCategory = actionCategory,
                parametersJson = parametersJson,
                languageCode = languageCode,
                wasOffline = wasOffline,
                status = status
            )
        )
    }

    suspend fun getRecentConversationContext(limit: Int = 6): List<CommandLogEntity> {
        return dao.getRecentLogs(limit).reversed()
    }

    suspend fun addReminder(title: String, date: String, time: String): Long {
        return dao.insertReminder(
            ReminderEntity(
                title = title,
                scheduledDate = date,
                scheduledTime = time
            )
        )
    }

    suspend fun toggleReminder(id: Long, completed: Boolean) {
        dao.setReminderCompleted(id, completed)
    }

    suspend fun deleteReminder(id: Long) {
        dao.deleteReminder(id)
    }

    suspend fun clearConversationHistory() {
        dao.clearAllLogs()
    }

    suspend fun wipeAllLocalData() {
        dao.clearAllLogs()
        dao.clearAllReminders()
    }
}
