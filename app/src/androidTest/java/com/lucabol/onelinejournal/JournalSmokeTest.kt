package com.lucabol.onelinejournal

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lucabol.onelinejournal.core.ConflictPolicy
import java.time.LocalDate
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalSmokeTest {
    @get:Rule(order = 0)
    val notificationPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val graph get() = (compose.activity.application as JournalApplication).graph

    @Before fun resetIsolatedEmulator() {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
            "These tests modify journal data and must run only on an isolated emulator."
        }
        runBlocking {
            graph.ready.await()
            check(graph.store.currentState().accountEmail == null) { "Use an emulator without a connected Google account." }
            val snapshot = graph.store.snapshot()
            graph.store.importSnapshot(snapshot.copy(entries = emptyList()), snapshot.generation, ConflictPolicy.KEEP_LOCAL, true)
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("A short sentence is enough.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("journal-list").performScrollToNode(hasText(text))
    }

    private fun editor() = compose.onNodeWithTag("journal-editor")

    @Test fun writePersistAndReopenJournalAndSettings() {
        editor().performTextInput("An emulator memory worth keeping.")
        scrollTo("Save line")
        compose.onNodeWithText("Save line").performClick()
        compose.waitUntil(10_000) {
            runBlocking { graph.store.snapshot().entries.any {
                it.journalDate == LocalDate.now().toString() && it.text == "An emulator memory worth keeping."
            } }
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Save changes").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tab-write").assertIsSelected()
        compose.onNodeWithTag("tab-years").performClick()
        scrollTo("An emulator memory worth keeping.")
        compose.onNodeWithText("An emulator memory worth keeping.").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        scrollTo("Your Google Drive")
        scrollTo("Connect Google Drive")
        compose.onNodeWithText("Connect Google Drive").assertIsDisplayed()
        scrollTo("Export and import")
        compose.onNodeWithText("Export and import").performClick()
        scrollTo("Import backup")
        compose.onNodeWithText("Import backup").assertIsDisplayed()
        val stored = runBlocking { graph.store.snapshot() }
        assertEquals(1, stored.entries.count { it.deletedAt == null })
        assertTrue(stored.generation > 0)
    }

    @Test fun draftsSurviveTabsSettingsRecreationAndReminderRouting() {
        editor().performTextInput("An unfinished reflection.")
        scrollTo("Another cue")
        compose.onNodeWithText("Another cue").performClick()
        compose.onNodeWithTag("tab-years").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("tab-years").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-years").assertIsSelected()
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[JournalViewModel::class.java].requestDate(LocalDate.now())
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Another cue").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tab-write").assertIsSelected()
        compose.onNodeWithTag("journal-list").performScrollToNode(hasTestTag("journal-editor"))
        editor().assertTextContains("An unfinished reflection.")
        compose.onNodeWithTag("journal-list").performScrollToNode(hasContentDescription("Previous day"))
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("journal-list").performScrollToNode(hasTestTag("journal-editor"))
        editor().assertTextContains("An unfinished reflection.")
        assertTrue(runBlocking { graph.store.snapshot().entries.isEmpty() })
    }

    @Test fun historicalEditsKeepTheOriginalDateAndPromptAndProtectDrafts() {
        val pastDate = LocalDate.now().minusYears(1)
        runBlocking { graph.store.save(pastDate, "Last year's quiet moment.", "The original question.", null) }
        editor().performTextInput("Today's unfinished line.")
        compose.onNodeWithTag("tab-years").performClick()
        scrollTo("Last year's quiet moment.")
        compose.onNodeWithText("Last year's quiet moment.").performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("tab-write").performClick()
        compose.onNodeWithTag("journal-list").performScrollToNode(hasTestTag("journal-editor"))
        editor().assertTextContains("Today's unfinished line.")
        compose.onNodeWithTag("tab-years").performClick()
        scrollTo("Last year's quiet moment.")
        compose.onNodeWithText("Last year's quiet moment.").performClick()
        compose.onNodeWithText("Discard and change date").performClick()
        compose.onNodeWithTag("tab-write").assertIsSelected()
        scrollTo("The original question.")
        compose.onNodeWithText("The original question.").assertIsDisplayed()
        compose.onNodeWithTag("journal-list").performScrollToNode(hasTestTag("journal-editor"))
        editor().performTextReplacement("An edited memory.\nStill one line.")
        editor().assertTextContains("An edited memory. Still one line.")
        scrollTo("Save changes")
        compose.onNodeWithText("Save changes").performClick()
        try {
            compose.waitUntil(10_000) {
                runBlocking { graph.store.snapshot().entries.singleOrNull()?.text == "An edited memory. Still one line." }
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Historical save mismatch: ${runBlocking { graph.store.snapshot().entries }}", e)
        }
        val stored = runBlocking { graph.store.snapshot().entries.single() }
        assertEquals(pastDate.toString(), stored.journalDate)
        assertEquals("The original question.", stored.promptText)
        scrollTo("Today")
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithTag("journal-list").performScrollToNode(hasTestTag("journal-editor"))
        editor().assertTextContains("")
        compose.onNodeWithText("Save line").assertExists()
    }

    @Test fun yearsIncludeEverySavedYearAndSystemBackReturnsToWrite() {
        runBlocking {
            for (offset in 0..7) {
                graph.store.save(LocalDate.now().minusYears(offset.toLong()), "Memory $offset.", "Cue $offset.", null)
            }
        }
        compose.onNodeWithTag("tab-years").performClick()
        for (offset in 0..7) {
            scrollTo("Memory $offset.")
            compose.onNodeWithText("Memory $offset.").assertIsDisplayed()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-years").assertIsSelected()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("tab-write").assertIsSelected()
        compose.onNodeWithTag("journal-list").performScrollToNode(hasTestTag("journal-editor"))
        editor().assertTextContains("Memory 0.")
        scrollTo("Entry options")
        compose.onNodeWithText("Entry options").performClick()
        compose.onNodeWithText("Delete line").performClick()
        compose.onNodeWithText("Delete this line?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(8, runBlocking { graph.store.snapshot().entries.count { it.deletedAt == null } })
    }

    @Test fun renderWritingAndYearsForDesignReview() {
        compose.runOnIdle {
            assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true") return
        runBlocking {
            graph.store.save(LocalDate.now().minusYears(1), "A long walk, without reaching for my phone.", "A cue", null)
            graph.store.save(LocalDate.now().minusYears(2), "I listened instead of trying to fix things.", "A cue", null)
        }
        compose.waitForIdle()
        SystemClock.sleep(500)
        capture("ritual-write.png")
        editor().performTextInput("I made room for a slower conversation.")
        scrollTo("Save line")
        compose.onNodeWithText("Save line").performClick()
        compose.onNodeWithTag("tab-years").performClick()
        scrollTo("This day, through the years")
        compose.waitForIdle()
        capture("ritual-years.png")
    }

    private fun capture(name: String) {
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
}
