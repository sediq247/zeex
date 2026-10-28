package com.zeex.data

import android.content.Context
import androidx.room.Room
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ZeexRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dao = Room.databaseBuilder(context, ZeexDatabase::class.java, "zeex.db").build().dao()
    private val functions by lazy { FirebaseFunctions.getInstance("us-central1") }
    fun chat(): Flow<List<ChatEntry>> = dao.observeChat()
    fun missions(): Flow<List<Mission>> = dao.observeMissions()
    fun reminders(): Flow<List<Reminder>> = dao.observeReminders()

    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) Tasks.await(auth.signInAnonymously())
        val result = Tasks.await(functions.getHttpsCallable(name).call(data))
        result.data as? Map<*, *> ?: emptyMap<Any, Any>()
    }

    suspend fun addChat(fromUser: Boolean, text: String): Boolean {
        val entry = ChatEntry(UUID.randomUUID().toString(), fromUser, text, System.currentTimeMillis())
        dao.putChat(entry)
        return runCatching {
            call("saveChat", mapOf("id" to entry.id, "fromUser" to fromUser, "text" to text, "createdAt" to entry.createdAt))
        }.isSuccess
    }

    suspend fun clearChat(): Boolean {
        dao.clearChat()
        return runCatching { call("clearChat") }.isSuccess
    }
    suspend fun refreshRemote() {
        val data = call("loadZeexData")
        val chatRows = (data["chat"] as? List<*>)?.mapNotNull { row ->
            val item = row as? Map<*, *> ?: return@mapNotNull null
            ChatEntry(item["id"].toString(), item["fromUser"] as? Boolean ?: false, item["text"] as? String ?: "", (item["createdAt"] as? Number)?.toLong() ?: 0L)
        }.orEmpty()
        val missions = (data["missions"] as? List<*>)?.mapNotNull { row ->
            val item = row as? Map<*, *> ?: return@mapNotNull null
            Mission(item["id"].toString(), item["title"] as? String ?: "", item["goal"] as? String ?: "", (item["status"] as? String ?: "ACTIVE").let { if (it == "COMPLETED") "DONE" else it }, (item["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis())
        }.orEmpty()
        val reminders = (data["reminders"] as? List<*>)?.mapNotNull { row ->
            val item = row as? Map<*, *> ?: return@mapNotNull null
            Reminder(item["id"].toString(), item["title"] as? String ?: "", (item["scheduledAt"] as? Number)?.toLong() ?: 0L, (item["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis())
        }.orEmpty()
        withContext(Dispatchers.IO) {
            if (chatRows.isNotEmpty()) dao.putChat(chatRows)
            if (missions.isNotEmpty()) dao.putMissions(missions)
            if (reminders.isNotEmpty()) dao.putReminders(reminders)
            reminders.filter { it.scheduledAt > System.currentTimeMillis() }.forEach { ReminderScheduler.schedule(appContext, it) }
        }
    }

    suspend fun createReminder(title: String, scheduledAt: Long): Reminder {
        val safeTitle = title.trim().take(180)
        require(safeTitle.isNotEmpty()) { "Reminder title cannot be empty." }
        require(scheduledAt > System.currentTimeMillis()) { "Choose a time in the future." }
        val reminder = Reminder(UUID.randomUUID().toString(), safeTitle, scheduledAt, System.currentTimeMillis())
        call("createReminder", mapOf("id" to reminder.id, "title" to reminder.title, "scheduledAt" to reminder.scheduledAt))
        dao.putReminder(reminder)
        ReminderScheduler.schedule(appContext, reminder)
        return reminder
    }

    suspend fun updateReminder(id: String, title: String, scheduledAt: Long) {
        val safeTitle = title.trim().take(180)
        require(safeTitle.isNotEmpty()) { "Reminder title cannot be empty." }
        require(scheduledAt > System.currentTimeMillis()) { "Choose a time in the future." }
        call("updateReminder", mapOf("id" to id, "title" to safeTitle, "scheduledAt" to scheduledAt))
        val old = dao.allReminders().firstOrNull { it.id == id }
        val updated = Reminder(id, safeTitle, scheduledAt, old?.createdAt ?: System.currentTimeMillis())
        dao.putReminder(updated)
        ReminderScheduler.cancel(appContext, id)
        ReminderScheduler.schedule(appContext, updated)
    }

    suspend fun cancelReminder(id: String) {
        call("cancelReminder", mapOf("id" to id))
        ReminderScheduler.cancel(appContext, id)
        dao.deleteReminder(id)
    }

    suspend fun upcomingReminders(): List<Reminder> = withContext(Dispatchers.IO) { dao.allReminders().filter { it.scheduledAt > System.currentTimeMillis() }.take(30) }

    suspend fun savedMissions(): List<Mission> = withContext(Dispatchers.IO) { dao.allMissions() }

    suspend fun createMission(title: String, goal: String): Mission {
        val safeTitle = title.trim().take(180)
        val safeGoal = goal.trim().take(1000)
        require(safeTitle.isNotEmpty() && safeGoal.isNotEmpty()) { "Mission title and goal are required." }
        val mission = Mission(UUID.randomUUID().toString(), safeTitle, safeGoal, "ACTIVE", System.currentTimeMillis())
        call("createMission", mapOf("id" to mission.id, "title" to mission.title, "goal" to mission.goal))
        dao.putMission(mission)
        return mission
    }

    suspend fun setMissionStatus(id: String, status: String) {
        val apiStatus = if (status == "DONE") "COMPLETED" else status
        call("setMissionStatus", mapOf("id" to id, "status" to apiStatus))
        dao.setMissionStatus(id, status)
    }

    companion object {
        @Volatile private var instance: ZeexRepository? = null
        fun get(context: Context): ZeexRepository = instance ?: synchronized(this) {
            instance ?: ZeexRepository(context.applicationContext).also { instance = it }
        }
    }
}








