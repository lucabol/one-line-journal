package com.lucabol.onelinejournal.ui

import com.lucabol.onelinejournal.data.JournalState
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSummaryTest {
    private val connected = JournalState(
        journalId = "journal", writerId = "writer", generation = 2, backedUpGeneration = 2,
        accountEmail = "test@example.invalid", folderId = "folder",
        lastBackupAt = "2026-10-20T18:00:00Z", cloudMessage = "All changes backed up to Google Drive.",
    )

    @Test fun `a local save does not imply a completed Drive backup`() {
        assertEquals("Saved on phone. Drive backup pending.", backupSummary(connected.copy(generation = 3)))
    }

    @Test fun `a connection without an uploaded snapshot is still pending`() {
        assertEquals("Saved on phone. Drive backup pending.", backupSummary(connected.copy(lastBackupAt = null)))
    }

    @Test fun `restore requirements take precedence over pending changes`() {
        assertEquals("Drive needs attention. Review backups.", backupSummary(connected.copy(restoreRequired = true, generation = 3)))
    }

    @Test fun `disconnected journals remain explicitly phone only`() {
        assertEquals("On this phone only", backupSummary(connected.copy(accountEmail = null)))
    }

    @Test fun `successful backup needs a timestamp acknowledged generation and success status`() {
        assertEquals("Drive backup up to date", backupSummary(connected))
        assertEquals("Drive needs attention. View status.", backupSummary(connected.copy(cloudMessage = "Reconnect Google Drive.")))
    }
}
