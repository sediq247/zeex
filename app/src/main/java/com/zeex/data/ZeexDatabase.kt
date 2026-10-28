package com.zeex.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat")
data class ChatEntry(@PrimaryKey val id: String, val fromUser: Boolean, val text: String, val createdAt: Long)

@Entity(tableName = "missions")
data class Mission(@PrimaryKey val id: String, val title: String, val goal: String, val status: String, val createdAt: Long)

@Entity(tableName = "reminders")
data class Reminder(@PrimaryKey val id: String, val title: String, val scheduledAt: Long, val createdAt: Long)

@Dao
interface ZeexDao {
    @Query("SELECT * FROM chat ORDER BY createdAt ASC") fun observeChat(): Flow<List<ChatEntry>>
    @Query("SELECT * FROM chat ORDER BY createdAt ASC") suspend fun allChat(): List<ChatEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChat(items: List<ChatEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChat(item: ChatEntry)
    @Query("DELETE FROM chat") suspend fun clearChat()

    @Query("SELECT * FROM missions ORDER BY createdAt DESC") fun observeMissions(): Flow<List<Mission>>
    @Query("SELECT * FROM missions ORDER BY createdAt DESC") suspend fun allMissions(): List<Mission>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMission(item: Mission)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMissions(items: List<Mission>)
    @Query("UPDATE missions SET status = :status WHERE id = :id") suspend fun setMissionStatus(id: String, status: String)

    @Query("SELECT * FROM reminders ORDER BY scheduledAt ASC") fun observeReminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminders ORDER BY scheduledAt ASC") suspend fun allReminders(): List<Reminder>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReminder(item: Reminder)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReminders(items: List<Reminder>)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun deleteReminder(id: String)
}

@Database(entities = [ChatEntry::class, Mission::class, Reminder::class], version = 1, exportSchema = false)
abstract class ZeexDatabase : RoomDatabase() {
    abstract fun dao(): ZeexDao
}
