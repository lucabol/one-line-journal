package com.lucabol.onelinejournal.reminders

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lucabol.onelinejournal.data.JournalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class RemindersTest {
    @Test fun `enabling schedules one replaceable local alarm and disabling removes it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reminders = Reminders(context)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        reminders.schedule(JournalSettings(remindersEnabled = true))
        assertEquals(1, alarms.scheduledAlarms.size)
        reminders.schedule(JournalSettings(remindersEnabled = true, reminderMinute = 22 * 60))
        assertEquals(1, alarms.scheduledAlarms.size)
        assertTrue(alarms.scheduledAlarms.single().triggerAtTime > System.currentTimeMillis())
        reminders.schedule(JournalSettings(remindersEnabled = false))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test fun `notification contains a cue and targets a specific calendar date`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reminders = Reminders(context)
        reminders.createChannel()
        val date = java.time.LocalDate.of(2024, 2, 29)
        reminders.show(date)
        val notification = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertNotNull(notification.extras.getCharSequence("android.text"))
        assertEquals(date.toString(), shadowOf(notification.contentIntent).savedIntent.getStringExtra(Reminders.DATE))
    }
}
