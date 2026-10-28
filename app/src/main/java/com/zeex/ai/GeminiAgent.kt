package com.zeex.ai

import android.content.Context
import android.os.BatteryManager
import android.os.StatFs
import com.google.firebase.Firebase
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.zeex.data.ZeexRepository
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface AgentResult {
    data class Success(val text: String) : AgentResult
    data class Failure(val message: String) : AgentResult
}

class GeminiAgent(context: Context, private val repository: ZeexRepository) {
    private val appContext = context.applicationContext
    private val declarations = listOf(
        FunctionDeclaration("create_reminder", "Create and persist a reminder, then schedule a notification on this Android device. Only call when the user clearly asks to set a reminder. whenIso must be an ISO-8601 date-time including timezone offset, for example 2026-11-01T09:00:00+01:00.", mapOf(
            "title" to Schema.string("Short reminder title."),
            "whenIso" to Schema.string("Future date and time in ISO-8601 including timezone offset."),
        )),
        FunctionDeclaration("create_mission", "Save a durable personal goal the user explicitly asks Zeex to track as a mission.", mapOf(
            "title" to Schema.string("Short mission title."),
            "goal" to Schema.string("Specific goal and intended outcome."),
        )),
        FunctionDeclaration("get_device_status", "Read current battery level, charging state, and free space allocated to the Zeex app. Do not infer whole-phone storage or temperature.", emptyMap()),
        FunctionDeclaration("get_upcoming_reminders", "List the user's saved upcoming reminders from local cache.", emptyMap()),
        FunctionDeclaration("get_saved_missions", "List saved mission titles, goals, and statuses.", emptyMap()),
        FunctionDeclaration("set_mission_status", "Update the status of a saved mission. Use ACTIVE, PAUSED, or DONE. Only do this when requested by the user.", mapOf(
            "id" to Schema.string("The id of the mission to update."),
            "status" to Schema.string("One of ACTIVE, PAUSED, or DONE."),
        )),
    )
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.functionDeclarations(declarations)),
        )
    }

    suspend fun respond(message: String, history: List<Pair<String, String>>): AgentResult = try {
        val now = ZonedDateTime.now()
        val context = history.takeLast(10).joinToString("\n") { (role, text) -> "$role: ${text.take(1200)}" }
        val prompt = """
            You are Zeex, the user's phone assistant. Address the user naturally as Boss when it fits. Be warm, direct, and honest. Use only tool results for device readings and saved data. Never claim an action succeeded unless its tool result has ok=true. Ask a short clarification if the requested time or goal is ambiguous. Do not claim to monitor private apps, device temperature, or whole-phone storage. Current time: $now.

            Recent conversation:
            $context

            User: $message
        """.trimIndent()
        val chat = model.startChat()
        var response = chat.sendMessage(prompt)
        repeat(3) {
            val calls = response.functionCalls
            if (calls.isEmpty()) return AgentResult.Success(response.text?.takeIf { it.isNotBlank() } ?: "I couldn't form a response just now.")
            val returned = calls.map { function ->
                val result = runCatching { execute(function.name, function.args) }
                    .getOrElse { mapOf("ok" to false, "error" to (it.message ?: "Action failed.")) }
                FunctionResponsePart(function.name, buildJsonObject {
                    result.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> put(key, JsonPrimitive(value))
                            is Number -> put(key, JsonPrimitive(value))
                            else -> put(key, JsonPrimitive(value?.toString() ?: ""))
                        }
                    }
                })
            }
            response = chat.sendMessage(content("user") { returned.forEach { part(it) } })
        }
        AgentResult.Success(response.text?.takeIf { it.isNotBlank() } ?: "I completed the available steps, but couldn't generate a final summary.")
    } catch (error: Exception) {
        AgentResult.Failure("I couldn't reach Gemini right now. ${error.message ?: "Check your connection and Firebase AI Logic setup."}")
    }

    private suspend fun execute(name: String, args: Map<String, Any?>): Map<String, Any?> = when (name) {
        "create_reminder" -> {
            val title = args["title"] as? String ?: error("Reminder title is missing.")
            val whenIso = args["whenIso"] as? String ?: error("Reminder time is missing.")
            val time = runCatching { OffsetDateTime.parse(whenIso).toInstant().toEpochMilli() }
                .getOrElse { runCatching { ZonedDateTime.parse(whenIso).toInstant().toEpochMilli() }.getOrElse { error("Use a future ISO date and time with a timezone.") } }
            val saved = repository.createReminder(title, time)
            mapOf("ok" to true, "id" to saved.id, "title" to saved.title, "scheduledAt" to saved.scheduledAt.toString())
        }
        "create_mission" -> {
            val title = args["title"] as? String ?: error("Mission title is missing.")
            val goal = args["goal"] as? String ?: error("Mission goal is missing.")
            val saved = repository.createMission(title, goal)
            mapOf("ok" to true, "id" to saved.id, "title" to saved.title, "goal" to saved.goal)
        }
        "get_device_status" -> {
            val battery = appContext.getSystemService(BatteryManager::class.java)
            val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val stat = StatFs(appContext.filesDir.path)
            mapOf("ok" to true, "batteryPercent" to level, "appFreeStorageMiB" to (stat.availableBytes / (1024 * 1024)), "observedAt" to System.currentTimeMillis().toString())
        }
        "get_upcoming_reminders" -> mapOf("ok" to true, "items" to repository.upcomingReminders().joinToString("; ") { "${it.title} at ${ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.scheduledAt), ZoneId.systemDefault())}" })
        "get_saved_missions" -> mapOf("ok" to true, "items" to repository.savedMissions().joinToString("; ") { "${it.id}: ${it.title} — ${it.goal} (${it.status})" })
        "set_mission_status" -> {
            val id = args["id"] as? String ?: error("Mission id is missing.")
            val status = (args["status"] as? String)?.uppercase() ?: error("Mission status is missing.")
            require(status in setOf("ACTIVE", "PAUSED", "DONE")) { "Status must be ACTIVE, PAUSED, or DONE." }
            repository.setMissionStatus(id, status)
            mapOf("ok" to true, "id" to id, "status" to status)
        }
        else -> mapOf("ok" to false, "error" to "Unknown tool: $name")
    }
}



