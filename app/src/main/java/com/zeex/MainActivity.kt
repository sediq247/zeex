package com.zeex

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeex.ai.AgentResult
import com.zeex.ai.GeminiAgent
import com.zeex.data.Mission
import com.zeex.data.Reminder
import com.zeex.data.ZeexRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Midnight = Color(0xFF080D18)
private val Panel = Color(0xFF111A2A)
private val Mint = Color(0xFF7DF9D0)
private val Muted = Color(0xFF9BAAC0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZeexApp() }
    }
}

private data class ChatMessage(val id: String, val fromUser: Boolean, val text: String)

@Composable
private fun ZeexApp() {
    val context = LocalContext.current
    val repo = remember { ZeexRepository.get(context) }
    val reminders by repo.reminders().collectAsStateWithLifecycle(initialValue = emptyList())
    val missions by repo.missions().collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(repo) { runCatching { repo.refreshRemote() } }
    var tab by rememberSaveable { mutableStateOf("Home") }
    val colors = darkColorScheme(background = Midnight, surface = Panel, primary = Mint, onPrimary = Midnight, onBackground = Color.White, onSurface = Color.White, secondary = Color(0xFFB8A8FF))
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = Midnight, bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1422), contentColor = Mint) {
                listOf("Home", "Chat", "Missions", "Device", "Settings").forEach { item ->
                    val icon = when (item) { "Home" -> Icons.Default.Home; "Chat" -> Icons.Default.ChatBubbleOutline; "Missions" -> Icons.Default.TrackChanges; "Device" -> Icons.Default.BatteryChargingFull; else -> Icons.Default.Settings }
                    NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(icon, contentDescription = item) }, label = { Text(item) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint, indicatorColor = Color(0xFF1A3440), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(Midnight, Color(0xFF101C2C), Midnight)))) {
                when (tab) {
                    "Home" -> HomeScreen(reminders.size, missions.size) { tab = it }
                    "Chat" -> ChatScreen(repo)
                    "Missions" -> MissionsScreen(missions, repo)
                    "Device" -> DeviceScreen(reminders, repo)
                    else -> SettingsScreen(repo)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(reminderCount: Int, missionCount: Int, navigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Z E E X", style = MaterialTheme.typography.labelLarge, color = Mint, fontWeight = FontWeight.Bold)
                Text("Your phone, more aware.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.size(48.dp).background(Color(0xFF12302F), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Mint) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("READY WHEN YOU ARE", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("Good to see you, Boss.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("I can help you plan, remember, and understand what your phone can actually see.", color = Muted)
                Button(onClick = { navigate("Chat") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Waves, null); Spacer(Modifier.width(8.dp)); Text("Talk to Zeex") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("REMINDERS", "$reminderCount active", Icons.Default.NotificationsActive, Modifier.weight(1f)) { navigate("Chat") }
            StatCard("MISSIONS", "$missionCount saved", Icons.Default.TrackChanges, Modifier.weight(1f)) { navigate("Missions") }
        }
        Text("QUICK ACCESS", color = Muted, style = MaterialTheme.typography.labelMedium)
        listOf("Check my phone", "Plan something", "Set a reminder").forEach { hint ->
            OutlinedButton(onClick = { navigate("Chat") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Text(hint, modifier = Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Mint)
            }
        }
        Text("Device readings are shown only when available. Zeex does not scan private app data.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(112.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, color = Muted, style = MaterialTheme.typography.labelSmall); Icon(icon, null, tint = Mint, modifier = Modifier.size(18.dp)) }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChatScreen(repo: ZeexRepository) {
    val context = LocalContext.current
    val agent = remember { GeminiAgent(context, repo) }
    val scope = rememberCoroutineScope()
    val savedMessages by repo.chat().collectAsStateWithLifecycle(initialValue = emptyList())
    val messages = remember(savedMessages) { savedMessages.map { ChatMessage(it.id, it.fromUser, it.text) } }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) input = heard
    }
    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        val history = messages.map { (if (it.fromUser) "user" else "assistant") to it.text }
        busy = true; error = null
        scope.launch {
            if (!repo.addChat(true, text)) error = "Your message is on this phone, but the server did not confirm Firebase sync."
            when (val result = agent.respond(text, history)) {
                is AgentResult.Success -> if (!repo.addChat(false, result.text)) error = "Reply saved on this phone; Firebase sync was not confirmed."
                is AgentResult.Failure -> { error = result.message; repo.addChat(false, result.message) }
            }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("CONVERSATION", color = Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Gemini-powered · actions use real capabilities", color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (message.fromUser) Color(0xFF164336) else Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                        Text(message.text, Modifier.padding(horizontal = 15.dp, vertical = 12.dp), color = Color.White)
                    }
                }
            }
            if (busy) item { Text("Zeex is thinking…", color = Mint, style = MaterialTheme.typography.bodySmall) }
        }
        error?.let { Text(it, color = Color(0xFFFFB4A9), style = MaterialTheme.typography.bodySmall) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask Zeex…") }, shape = RoundedCornerShape(18.dp), maxLines = 4)
            IconButton(onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Zeex")
                runCatching { speech.launch(intent) }.onFailure { error = "Speech recognition is unavailable on this device." }
            }) { Icon(Icons.Default.Mic, "Speak", tint = Mint) }
            IconButton(onClick = ::send, enabled = !busy && input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (!busy && input.isNotBlank()) Mint else Muted) }
        }
    }
}

@Composable
private fun MissionsScreen(missions: List<Mission>, repo: ZeexRepository) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("MISSIONS", color = Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Goals saved for follow-through", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(18.dp))
        if (missions.isEmpty()) Text("No missions yet. Ask Zeex to save a goal from Chat.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(missions, key = { it.id }) { mission ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(mission.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(mission.status, color = Mint, style = MaterialTheme.typography.labelSmall) }
                        Text(mission.goal, color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (mission.status == "ACTIVE") TextButton(onClick = { scope.launch { runCatching { repo.setMissionStatus(mission.id, "PAUSED") }.onFailure { error = it.message } } }) { Text("Pause") }
                            if (mission.status == "PAUSED") TextButton(onClick = { scope.launch { runCatching { repo.setMissionStatus(mission.id, "ACTIVE") }.onFailure { error = it.message } } }) { Text("Resume") }
                            if (mission.status != "DONE" && mission.status != "CANCELLED") TextButton(onClick = { scope.launch { runCatching { repo.setMissionStatus(mission.id, "DONE") }.onFailure { error = it.message } } }) { Text("Mark done") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceScreen(reminders: List<Reminder>, repo: ZeexRepository) {
    val scope = rememberCoroutineScope()
    var editReminder by remember { mutableStateOf<Reminder?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editTime by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val manager = remember { context.getSystemService(android.os.BatteryManager::class.java) }
    val level = remember { manager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    val batteryIntent = remember { context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val batteryStatus = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL
    val stat = remember { android.os.StatFs(context.filesDir.path) }
    val available = stat.availableBytes / (1024 * 1024)
    val total = stat.totalBytes / (1024 * 1024)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("DEVICE HEALTH", color = Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("What Zeex can see", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Current Android readings. This is not a full storage scan or hardware diagnostic.", color = Muted)
        HealthRow("Battery", if (level in 0..100) "$level%" else "Not available", if (charging) "Charging" else "Not charging")
        HealthRow("Zeex app storage", "$available MB free", "of $total MB allocated")
        HealthRow("Reminders", "${reminders.size} active", "Saved locally on this phone")
        syncError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        HealthRow("Temperature", "Not exposed", "This device does not provide a reliable reading to Zeex")
        Spacer(Modifier.height(8.dp))
        Text("UPCOMING REMINDERS", color = Muted, style = MaterialTheme.typography.labelMedium)
        if (reminders.isEmpty()) Text("No reminders yet. Ask Zeex to create one from Chat.", color = Muted)
        reminders.take(20).forEach { reminder ->
            ReminderCard(
                reminder,
                onEdit = {
                    editReminder = it
                    editTitle = it.title
                    editTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it.scheduledAt))
                    editError = null
                },
                onCancel = { id -> scope.launch { runCatching { repo.cancelReminder(id) }.onFailure { syncError = it.message } } }
            )
        }
        editReminder?.let { selected ->
            AlertDialog(
                onDismissRequest = { editReminder = null },
                title = { Text("Edit reminder") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") }, singleLine = true)
                        OutlinedTextField(value = editTime, onValueChange = { editTime = it }, label = { Text("Date and time with timezone") }, supportingText = { Text("Example: 2026-10-24T09:30:00+01:00") })
                        editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val millis = runCatching { java.time.OffsetDateTime.parse(editTime).toInstant().toEpochMilli() }.getOrNull()
                        if (millis == null) editError = "Enter an ISO date and time with timezone."
                        else scope.launch {
                            runCatching { repo.updateReminder(selected.id, editTitle, millis) }
                                .onSuccess { editReminder = null }
                                .onFailure { editError = it.message ?: "Could not update reminder." }
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editReminder = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun ReminderCard(reminder: Reminder, onEdit: (Reminder) -> Unit, onCancel: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontWeight = FontWeight.Medium)
                Text(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(reminder.scheduledAt)), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onEdit(reminder) }) { Text("Edit") }
            TextButton(onClick = { onCancel(reminder.id) }) { Text("Cancel") }
        }
    }
}
@Composable
private fun HealthRow(title: String, value: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall) }
            Text(value, color = Mint, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsScreen(repo: ZeexRepository) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Firebase account and cloud sync status.") }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        status = if (allowed) "Notifications are enabled." else "Notifications are disabled; reminders will not appear as notifications."
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("SETTINGS", color = Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Your controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("AI provider", style = MaterialTheme.typography.titleMedium)
        Text("Firebase AI Logic · gemini-3.8-flash", color = Muted)
        Text("Gemini calls are protected by Firebase App Check. Chat and tasks sync to Firestore through authenticated Firebase Functions.", color = Muted, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = Color(0xFF263246))
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text("Chat is stored locally and in your private Firestore account. Gemini access is provided by Firebase AI Logic.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { scope.launch { status = if (repo.clearChat()) "Conversation history deleted from this phone and Firestore." else "Deleted from this phone; Firebase deletion was not confirmed." } }) { Text("Delete conversation history") }
        HorizontalDivider(color = Color(0xFF263246))
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else status = "Notifications are available on this Android version."
        }) { Text("Enable reminder notifications") }
        Text(status, color = Mint, style = MaterialTheme.typography.bodySmall)
        Text("Voice input uses Android's speech recognizer. Zeex does not listen in the background.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}




