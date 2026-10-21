package com.lucabol.onelinejournal.core

import java.time.LocalDate
import java.time.MonthDay
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

internal fun entry(date: String, text: String = "A moment worth keeping.", deleted: Boolean = false) = JournalEntry(
    journalDate = date,
    text = if (deleted) "" else text,
    promptText = if (deleted) "" else Prompts.forDate(LocalDate.parse(date)),
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T01:00:00Z",
    deletedAt = if (deleted) "2026-01-01T01:00:00Z" else null,
)

internal fun snapshot(entries: List<JournalEntry> = listOf(entry("2026-10-19"))) = JournalSnapshot(
    journalId = UUID.randomUUID().toString(),
    writerId = UUID.randomUUID().toString(),
    generation = 3,
    exportedAt = "2026-10-19T20:00:00Z",
    entries = entries,
)

class JournalTest {
    @Test fun `same month and day includes every year but excludes neighbors and deletions`() {
        val entries = listOf(entry("2026-10-19"), entry("2025-10-19"), entry("2010-10-19"),
            entry("2024-10-18"), entry("2023-10-19", deleted = true))
        assertEquals(listOf("2026-10-19", "2025-10-19", "2010-10-19"),
            JournalFormat.onDay(entries, MonthDay.of(10, 19)).map { it.journalDate })
    }

    @Test fun `leap day never becomes March first`() {
        val entries = listOf(entry("2024-02-29"), entry("2020-02-29"), entry("2023-03-01"), entry("2024-03-01"))
        assertEquals(2, JournalFormat.onDay(entries, MonthDay.of(2, 29)).size)
        assertEquals(listOf("2024-03-01", "2023-03-01"),
            JournalFormat.onDay(entries, MonthDay.of(3, 1)).map { it.journalDate })
    }

    @Test fun `portable JSON round trips Unicode and exact whitespace`() {
        val original = snapshot(listOf(entry("2026-10-19", "  Caf\u00e9, \u96e8, and a smile \uD83D\uDE42.  ")))
        assertEquals(original, JournalFormat.decode(JournalFormat.encode(original)))
    }

    @Test fun `invalid dates and blank or multiline entries are rejected`() {
        listOf(entry("2026-10-19").copy(journalDate = "2023-02-29"), entry("2026-10-19", ""), entry("2026-10-19", "two\nlines"),
            entry("2026-10-19", "x".repeat(MAX_ENTRY_LENGTH + 1))).forEach {
            assertFails { it.validate() }
        }
    }

    @Test fun `backup rejects duplicate dates unsupported versions and reversed timestamps`() {
        val duplicate = entry("2026-10-19")
        assertFails { snapshot(listOf(duplicate, duplicate)).validate() }
        assertFails { snapshot().copy(schemaVersion = 2).validate() }
        assertFails { duplicate.copy(updatedAt = "2025-01-01T00:00:00Z").validate() }
        val json = JournalFormat.encode(snapshot()).toString(Charsets.UTF_8).replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        assertFails { JournalFormat.decode(json.toByteArray()) }
    }

    @Test fun `unknown optional fields do not destroy a compatible backup`() {
        val original = snapshot()
        val json = JournalFormat.encode(original).toString(Charsets.UTF_8).replaceFirst("{", "{\"futureNote\": true,")
        assertEquals(original, JournalFormat.decode(json.toByteArray()))
    }

    @Test fun `import previews every conflicting date including deletions`() {
        val local = listOf(entry("2026-10-19", "Local"), entry("2025-10-19", "Old"))
        val incoming = listOf(entry("2026-10-19", "Backup"), entry("2025-10-19", deleted = true), entry("2024-10-19"))
        val preview = JournalMerge.preview(local, incoming)
        assertEquals(listOf("2025-10-19", "2026-10-19"), preview.conflictDates)
        assertEquals(1, preview.newDates)
        assertEquals("Local", JournalMerge.merge(local, incoming, ConflictPolicy.KEEP_LOCAL).last().text)
        val restored = JournalMerge.merge(local, incoming, ConflictPolicy.USE_BACKUP)
        assertTrue(restored.first { it.journalDate == "2025-10-19" }.deletedAt != null)
        assertEquals("Backup", restored.last().text)
    }

    @Test fun `readable export excludes deleted content and includes prompts`() {
        val text = JournalFormat.readable(snapshot(listOf(entry("2026-10-19", "Visible"), entry("2025-10-19", deleted = true))))
        assertTrue(text.contains("Visible"))
        assertTrue(text.contains("Prompt:"))
        assertFalse(text.contains("2025-10-19"))
    }

    @Test fun `prompt is stable across years even after leap day`() {
        for (month in 1..12) {
            for (day in 1..28) {
                assertEquals(Prompts.forDate(LocalDate.of(2023, month, day)), Prompts.forDate(LocalDate.of(2024, month, day)))
            }
        }
        assertTrue(Prompts.forDate(LocalDate.of(2024, 2, 29)).isNotBlank())
        assertFalse(Prompts.forDate(LocalDate.of(2024, 3, 1)) == Prompts.forDate(LocalDate.of(2024, 3, 1), 1))
    }
}
