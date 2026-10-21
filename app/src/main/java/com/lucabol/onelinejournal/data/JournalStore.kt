package com.lucabol.onelinejournal.data

import androidx.room.withTransaction
import com.lucabol.onelinejournal.core.ConflictPolicy
import com.lucabol.onelinejournal.core.JournalEntry
import com.lucabol.onelinejournal.core.JournalMerge
import com.lucabol.onelinejournal.core.JournalSnapshot
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.map

class JournalStore(private val database: JournalDatabase) {
    private val dao = database.journalDao()
    val entries = dao.observeEntries().map { rows -> rows.map(EntryRow::entry) }
    val state = dao.observeState()

    suspend fun initialize() = database.withTransaction {
        if (dao.state() == null) {
            dao.putState(JournalState(journalId = UUID.randomUUID().toString(), writerId = UUID.randomUUID().toString()))
        }
    }

    suspend fun currentState(): JournalState = checkNotNull(dao.state()) { "Journal is not initialized." }

    suspend fun snapshot(): JournalSnapshot = database.withTransaction {
        val state = currentState()
        JournalSnapshot(
            journalId = state.journalId,
            writerId = state.writerId,
            parentSnapshotId = state.baselineSnapshotId,
            generation = state.generation,
            exportedAt = Instant.now().toString(),
            entries = dao.entries().map(EntryRow::entry),
        )
    }

    suspend fun save(date: LocalDate, text: String, prompt: String, expectedRevision: Long?) = database.withTransaction {
        require(!date.isAfter(LocalDate.now())) { "Choose today or an earlier date." }
        val old = dao.entry(date.toString())?.entry()
        require(old?.revision == expectedRevision) { "This entry changed while you were editing. Reopen it before saving." }
        val now = timestamp(old)
        val entry = JournalEntry(
            date.toString(), text, prompt, old?.createdAt ?: now, now, (old?.revision ?: 0) + 1,
        ).also(JournalEntry::validate)
        dao.putEntry(EntryRow.from(entry))
        val state = currentState()
        dao.putState(state.copy(generation = state.generation + 1))
    }

    suspend fun delete(date: String, expectedRevision: Long) = database.withTransaction {
        val old = requireNotNull(dao.entry(date)) { "This entry no longer exists." }.entry()
        require(old.revision == expectedRevision) { "This entry changed. Reopen it before deleting." }
        val now = timestamp(old)
        dao.putEntry(EntryRow.from(old.copy(text = "", promptText = "", updatedAt = now,
            revision = old.revision + 1, deletedAt = now)))
        val state = currentState()
        dao.putState(state.copy(generation = state.generation + 1))
    }

    suspend fun updateState(transform: (JournalState) -> JournalState) = database.withTransaction {
        dao.putState(transform(currentState()))
    }

    suspend fun markPending() = updateState { it.copy(generation = it.generation + 1) }

    suspend fun acknowledgeBackup(generation: Long, fileId: String, snapshotId: String, at: String) = updateState {
        require(generation <= it.generation) { "The cloud backup is ahead of this phone. Restore before writing." }
        it.copy(
            backedUpGeneration = maxOf(it.backedUpGeneration, generation),
            baselineFileId = fileId,
            baselineSnapshotId = snapshotId,
            lastBackupAt = at,
            cloudMessage = if (it.generation == generation) "All changes backed up to Google Drive."
                else "Saved on this phone. Newer changes are waiting for backup.",
        )
    }

    suspend fun importSnapshot(
        incoming: JournalSnapshot,
        expectedGeneration: Long,
        policy: ConflictPolicy,
        replaceAll: Boolean,
        cloudFileId: String? = null,
        cloudSnapshotId: String? = null,
    ) = database.withTransaction {
        incoming.validate()
        val state = currentState()
        require(state.generation == expectedGeneration) { "The journal changed during preview. Preview the backup again." }
        val local = dao.entries().map(EntryRow::entry)
        val merged = if (replaceAll) incoming.entries else JournalMerge.merge(local, incoming.entries, policy)
        dao.clearEntries()
        dao.putEntries(merged.map(EntryRow::from))
        dao.putState(state.copy(
            journalId = if (cloudFileId != null) incoming.journalId else state.journalId,
            generation = maxOf(state.generation, incoming.generation) + 1,
            backedUpGeneration = if (cloudFileId != null) 0 else state.backedUpGeneration,
            baselineFileId = cloudFileId ?: state.baselineFileId,
            baselineSnapshotId = cloudSnapshotId ?: state.baselineSnapshotId,
            restoreRequired = if (cloudFileId != null) false else state.restoreRequired,
            cloudMessage = "Import saved on this phone. Waiting for backup.",
        ))
    }

    private fun timestamp(old: JournalEntry?): String =
        maxOf(Instant.now(), old?.let { Instant.parse(it.updatedAt) } ?: Instant.MIN).toString()
}
