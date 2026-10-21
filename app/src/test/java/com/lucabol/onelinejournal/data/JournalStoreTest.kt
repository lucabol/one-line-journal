package com.lucabol.onelinejournal.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lucabol.onelinejournal.core.ConflictPolicy
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class JournalStoreTest {
    private lateinit var database: JournalDatabase
    private lateinit var store: JournalStore

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), JournalDatabase::class.java)
            .allowMainThreadQueries().build()
        store = JournalStore(database)
        store.initialize()
    }

    @After fun close() { database.close() }

    @Test fun `saving increments generation atomically and persists exact calendar date`() = runBlocking {
        store.save(LocalDate.of(2024, 2, 29), "A leap-day memory", "A cue", null)
        val snapshot = store.snapshot()
        assertEquals(1, snapshot.entries.size)
        assertEquals("2024-02-29", snapshot.entries.single().journalDate)
        assertEquals(1L, snapshot.generation)
        assertEquals(0L, store.currentState().backedUpGeneration)
    }

    @Test fun `acknowledging an older upload cannot acknowledge edits made during it`() = runBlocking {
        store.save(LocalDate.of(2024, 10, 19), "First", "Cue", null)
        val captured = store.snapshot()
        store.save(LocalDate.of(2024, 10, 19), "Second", "Cue", 1)
        store.acknowledgeBackup(captured.generation, "file", captured.snapshotId, captured.exportedAt)
        val state = store.currentState()
        assertEquals(2L, state.generation)
        assertEquals(1L, state.backedUpGeneration)
        assertTrue(state.cloudMessage.contains("waiting"))
    }

    @Test fun `stale edits fail without losing either stored data or generation`() = runBlocking {
        store.save(LocalDate.of(2024, 10, 19), "Original", "Cue", null)
        var failed = false
        try { store.save(LocalDate.of(2024, 10, 19), "Stale", "Cue", null) } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
        assertEquals("Original", store.snapshot().entries.single().text)
        assertEquals(1L, store.currentState().generation)
    }

    @Test fun `deletion is a redacted tombstone and remains pending for backup`() = runBlocking {
        store.save(LocalDate.of(2024, 10, 19), "Sensitive original", "Cue", null)
        store.delete("2024-10-19", 1)
        val deleted = store.snapshot().entries.single()
        assertEquals("", deleted.text)
        assertEquals("", deleted.promptText)
        assertNotNull(deleted.deletedAt)
        assertEquals(2L, store.currentState().generation)
    }

    @Test fun `import rechecks preview generation inside its transaction`() = runBlocking {
        store.save(LocalDate.of(2024, 10, 19), "Original", "Cue", null)
        val incoming = store.snapshot()
        store.save(LocalDate.of(2024, 10, 20), "Added during preview", "Cue", null)
        var failed = false
        try { store.importSnapshot(incoming, 1, ConflictPolicy.USE_BACKUP, true) }
        catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
        assertEquals(2, store.snapshot().entries.size)
    }

    @Test fun `initial state does not schedule empty backup and identity survives reinitialization`() = runBlocking {
        val initial = store.currentState()
        store.initialize()
        assertEquals(initial.journalId, store.currentState().journalId)
        assertFalse(initial.generation > initial.backedUpGeneration)
    }
}
