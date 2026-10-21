package com.lucabol.onelinejournal.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lucabol.onelinejournal.JournalApplication
import com.lucabol.onelinejournal.MainActivity
import com.lucabol.onelinejournal.R
import com.lucabol.onelinejournal.core.Prompts
import com.lucabol.onelinejournal.core.ReminderTime
import com.lucabol.onelinejournal.data.JournalSettings
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class Reminders(private val context: Context) {
    companion object {
        const val CHANNEL = "daily-journal"
        const val DATE = "journal_date"
        private const val REQUEST = 19
    }

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.reminder_channel_description) })
    }

    fun allowed(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return manager.areNotificationsEnabled() &&
            (manager.getNotificationChannel(CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) != NotificationManager.IMPORTANCE_NONE
    }

    fun schedule(settings: JournalSettings) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val base = Intent(context, ReminderReceiver::class.java)
        val old = PendingIntent.getBroadcast(context, REQUEST, base, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (old != null) manager.cancel(old)
        if (!settings.remindersEnabled) return
        val next = ReminderTime.next(ZonedDateTime.now(), settings.reminderMinute)
        val intent = base.putExtra(DATE, next.toLocalDate().toString())
        val pending = PendingIntent.getBroadcast(context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }

    fun show(date: LocalDate, prompt: String = Prompts.forDate(date)) {
        if (!allowed() || (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(DATE, date.toString()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 20, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(prompt)
            .setStyle(NotificationCompat.BigTextStyle().bigText(prompt))
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.reminder_write), open)
            .build()
        NotificationManagerCompat.from(context).notify(21, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val graph = (context.applicationContext as JournalApplication).graph
        graph.scope.launch {
            try {
                graph.ready.await()
                val settings = graph.settings.flow.first()
                graph.reminders.schedule(settings)
                val raw = intent.getStringExtra(Reminders.DATE) ?: return@launch
                val date = LocalDate.parse(raw)
                if (!settings.remindersEnabled || date != LocalDate.now()) return@launch
                val existing = graph.store.snapshot().entries.firstOrNull { it.journalDate == raw && it.deletedAt == null }
                val delivered = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)
                if (delivered.getString("last_date", null) != raw && !(settings.suppressCompleted && existing != null) && graph.reminders.allowed()) {
                    graph.reminders.show(date, existing?.promptText ?: Prompts.forDate(date))
                    check(delivered.edit().putString("last_date", raw).commit()) { "Could not record reminder delivery." }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OneLineReminder", "Reminder could not run (${e.javaClass.simpleName}). Open the app to reschedule.")
            } finally { pending.finish() }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        val graph = (context.applicationContext as JournalApplication).graph
        graph.scope.launch {
            try {
                graph.ready.await()
                graph.reminders.schedule(graph.settings.flow.first())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OneLineReminder", "Reminder rescheduling failed (${e.javaClass.simpleName}).")
            } finally { pending.finish() }
        }
    }
}
