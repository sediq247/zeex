package com.zeex.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zeex.MainActivity
import com.zeex.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderScheduler {
    private const val CHANNEL_ID = "zeex_reminders"
    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, reminder.id, reminder.title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.scheduledAt, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.scheduledAt, pending)
        }
    }
    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, id, ""))
    }
    private fun pendingIntent(context: Context, id: String, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("reminder_id", id).putExtra("title", title)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("zeex_reminders", "Zeex reminders", NotificationManager.IMPORTANCE_HIGH))
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "zeex_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Zeex reminder")
            .setContentText(intent.getStringExtra("title") ?: "A reminder is due")
            .setContentIntent(openApp).setAutoCancel(true).build()
        manager.notify(intent.getStringExtra("reminder_id")?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = androidx.room.Room.databaseBuilder(context, ZeexDatabase::class.java, "zeex.db").build().dao()
                dao.allReminders().filter { it.scheduledAt > System.currentTimeMillis() }.forEach { ReminderScheduler.schedule(context, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
