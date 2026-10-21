package com.lucabol.onelinejournal.core

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class BackupRulesTest {
    private fun remote(local: JournalSnapshot, file: String = "file", parent: String? = local.parentSnapshotId) = RemoteBackup(
        file, local.snapshotId, parent, local.journalId, local.writerId, local.generation,
        local.exportedAt, false, "digest",
    )

    @Test fun `new folder permits upload but deleted baseline does not`() {
        val local = snapshot()
        assertEquals(BackupDecision.Upload, BackupRules.decide(local, null, emptyList()))
        assertIs<BackupDecision.Conflict>(BackupRules.decide(local, "lost", emptyList()))
    }

    @Test fun `known current head permits next generation`() {
        val local = snapshot()
        assertEquals(BackupDecision.Upload, BackupRules.decide(local, "file", listOf(remote(local))))
    }

    @Test fun `crash after upload before acknowledgement recovers own upload`() {
        val parent = UUID.randomUUID().toString()
        val local = snapshot().copy(parentSnapshotId = parent)
        assertIs<BackupDecision.RecoverUpload>(BackupRules.decide(local, "old", listOf(remote(local))))
    }

    @Test fun `foreign writer and different journal never overwrite`() {
        val local = snapshot()
        assertIs<BackupDecision.Conflict>(BackupRules.decide(local, null,
            listOf(remote(local).copy(writerId = UUID.randomUUID().toString()))))
        assertIs<BackupDecision.Conflict>(BackupRules.decide(local, null,
            listOf(remote(local).copy(journalId = UUID.randomUUID().toString()))))
    }

    @Test fun `unacknowledged branches are retained for explicit restore`() {
        val parent = UUID.randomUUID().toString()
        val local = snapshot().copy(parentSnapshotId = parent)
        val own = remote(local)
        val other = remote(local, "other").copy(snapshotId = UUID.randomUUID().toString(), writerId = UUID.randomUUID().toString())
        assertIs<BackupDecision.Conflict>(BackupRules.decide(local, "old", listOf(own, other)))
    }

    @Test fun `explicitly restored head resolves historical branches`() {
        val local = snapshot()
        val parent = UUID.randomUUID().toString()
        val selectedHead = remote(local, "selected", parent)
        val olderBranch = remote(local, "older", parent).copy(snapshotId = UUID.randomUUID().toString())
        assertEquals(BackupDecision.Upload,
            BackupRules.decide(local, "selected", listOf(selectedHead, olderBranch)))
        val newer = remote(local.copy(parentSnapshotId = selectedHead.snapshotId), "new")
        assertIs<BackupDecision.RecoverUpload>(
            BackupRules.decide(local.copy(parentSnapshotId = selectedHead.snapshotId), "selected",
                listOf(newer, selectedHead, olderBranch)))
    }
}
