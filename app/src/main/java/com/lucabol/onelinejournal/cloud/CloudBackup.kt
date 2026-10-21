package com.lucabol.onelinejournal.cloud

import com.lucabol.onelinejournal.AppGraph
import com.lucabol.onelinejournal.core.BackupDecision
import com.lucabol.onelinejournal.core.BackupEncryption
import com.lucabol.onelinejournal.core.BackupRules
import com.lucabol.onelinejournal.core.ConflictPolicy
import com.lucabol.onelinejournal.core.JournalSnapshot
import com.lucabol.onelinejournal.core.RemoteBackup
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

data class CloudRestore(
    val snapshot: JournalSnapshot,
    val source: RemoteBackup,
    val head: RemoteBackup,
)

class CloudBackup(private val graph: AppGraph) {
    suspend fun connect(token: String, selectedFolder: DriveFolder? = null): List<DriveFolder> =
        graph.cloudMutex.withLock {
            val api = DriveApi(token, onUnauthorized = { graph.authorization.invalidate(token) })
            val account = api.account()
            val old = graph.store.currentState()
            require(old.accountId == null || old.accountId == account.id) {
                "This is a different Google account. Disconnect the current account first; local entries will remain."
            }
            val folders = api.folders()
            if (selectedFolder == null && folders.size > 1) return@withLock folders
            val folder = selectedFolder?.also { chosen ->
                require(folders.any { it.id == chosen.id }) { "The selected Drive folder is no longer available." }
            } ?: folders.singleOrNull() ?: api.createFolder()
            val existing = api.backups(folder.id)
            val reconnectSame = old.folderId == folder.id && !old.restoreRequired
            graph.store.updateState {
                it.copy(accountEmail = account.email, accountId = account.id, folderId = folder.id,
                    restoreRequired = existing.isNotEmpty() && !reconnectSame,
                    baselineFileId = if (reconnectSame) it.baselineFileId else null,
                    baselineSnapshotId = if (reconnectSame) it.baselineSnapshotId else null,
                    backedUpGeneration = if (reconnectSame) it.backedUpGeneration else 0,
                    lastBackupAt = if (reconnectSame) it.lastBackupAt else null,
                    cloudMessage = if (existing.isNotEmpty() && !reconnectSame)
                        "Backups found. Preview and restore before this phone can upload."
                    else "Google Drive connected. Changes will be backed up automatically.")
            }
            emptyList()
        }

    suspend fun disconnect() = graph.cloudMutex.withLock {
        graph.store.updateState {
            it.copy(accountEmail = null, accountId = null, folderId = null,
                baselineFileId = null, baselineSnapshotId = null, backedUpGeneration = 0,
                lastBackupAt = null, restoreRequired = false,
                cloudMessage = "Google Drive disconnected. Existing cloud backups were not deleted.")
        }
    }

    suspend fun list(): List<RemoteBackup> = graph.cloudMutex.withLock {
        val state = graph.store.currentState()
        val api = api()
        api.backups(requireNotNull(state.folderId) { "Connect Google Drive first." })
    }

    suspend fun preview(source: RemoteBackup?, passphrase: CharArray?): CloudRestore = graph.cloudMutex.withLock {
        val state = graph.store.currentState()
        val api = api()
        val backups = api.backups(requireNotNull(state.folderId) { "Connect Google Drive first." })
        val head = backups.firstOrNull() ?: error("No Google Drive backup is available.")
        val selected = source?.let { ref -> backups.firstOrNull { it.fileId == ref.fileId } }
            ?: if (source == null) head else error("That backup is no longer available.")
        val snapshot = BackupEncryption.decode(api.download(selected), passphrase)
        require(snapshot.journalId == selected.journalId && snapshot.snapshotId == selected.snapshotId &&
            snapshot.writerId == selected.writerId && snapshot.generation == selected.generation) {
            "The backup contents do not match its cloud metadata."
        }
        CloudRestore(snapshot, selected, head)
    }

    suspend fun restore(
        preview: CloudRestore,
        expectedGeneration: Long,
        policy: ConflictPolicy,
        replaceAll: Boolean,
        passphrase: CharArray?,
    ) = graph.cloudMutex.withLock {
        val state = graph.store.currentState()
        val latest = api().backups(requireNotNull(state.folderId)).firstOrNull()
        require(latest?.fileId == preview.head.fileId) { "Drive changed during preview. Preview the backup again." }
        graph.safetyCopy()
        if (preview.source.encrypted) {
            graph.vault.save(requireNotNull(passphrase) { "Enter this backup's recovery passphrase before restoring." })
            graph.settings.encryption(true)
        }
        graph.store.importSnapshot(preview.snapshot, expectedGeneration, policy, replaceAll,
            cloudFileId = preview.head.fileId, cloudSnapshotId = preview.head.snapshotId)
    }

    suspend fun uploadPending() = graph.cloudMutex.withLock {
        var state = graph.store.currentState()
        if (state.folderId == null || state.accountEmail == null || state.restoreRequired ||
            state.generation <= state.backedUpGeneration) return@withLock
        val api = api()
        require(api.account().id == state.accountId) { "The authorized Google account changed. Reconnect before uploading." }
        var snapshot = graph.store.snapshot()
        val folder = requireNotNull(state.folderId)
        val before = api.backups(folder)
        when (val decision = BackupRules.decide(snapshot, state.baselineFileId, before)) {
            is BackupDecision.Conflict -> {
                graph.store.updateState { it.copy(restoreRequired = true, cloudMessage = decision.message) }
                return@withLock
            }
            is BackupDecision.RecoverUpload -> {
                val bytes = api.download(decision.backup)
                if (decision.backup.generation == snapshot.generation) {
                    val passphrase = if (decision.backup.encrypted) graph.vault.load() else null
                    val uploaded = try { BackupEncryption.decode(bytes, passphrase) }
                        finally { passphrase?.fill('\u0000'); bytes.fill(0) }
                    require(uploaded.snapshotId == decision.backup.snapshotId &&
                        uploaded.journalId == snapshot.journalId && uploaded.entries == snapshot.entries) {
                        "An unfinished cloud upload does not match this journal. Restore before uploading."
                    }
                } else {
                    bytes.fill(0)
                }
                graph.store.acknowledgeBackup(decision.backup.generation, decision.backup.fileId,
                    decision.backup.snapshotId, decision.backup.createdAt)
                state = graph.store.currentState()
                if (state.generation <= state.backedUpGeneration) return@withLock
                snapshot = graph.store.snapshot()
            }
            BackupDecision.Upload -> Unit
        }
        val settings = graph.settings.flow.first()
        val password = if (settings.encryptionEnabled) graph.vault.load() else null
        val bytes = try { BackupEncryption.encode(snapshot, password) } finally { password?.fill('\u0000') }
        try {
            graph.store.updateState { it.copy(cloudMessage = "Saved on this phone. Uploading backup to Google Drive...") }
            val uploaded = api.upload(folder, bytes, RemoteBackup("", snapshot.snapshotId, snapshot.parentSnapshotId,
                snapshot.journalId, snapshot.writerId, snapshot.generation, Instant.now().toString(),
                settings.encryptionEnabled, DriveApi.digest(bytes)))
            val after = api.backups(folder)
            val decision = BackupRules.decide(snapshot, state.baselineFileId, after)
            if (decision is BackupDecision.Conflict || after.firstOrNull()?.fileId != uploaded.fileId) {
                graph.store.updateState { it.copy(restoreRequired = true,
                    cloudMessage = "Concurrent Drive changes detected. Both backups were kept. Preview and restore before uploading again.") }
                return@withLock
            }
            graph.store.acknowledgeBackup(snapshot.generation, uploaded.fileId, uploaded.snapshotId, uploaded.createdAt)
            after.drop(30).forEach { api.trash(it.fileId) }
        } finally { bytes.fill(0) }
    }

    private suspend fun api(): DriveApi {
        val state = graph.store.currentState()
        val token = graph.authorization.backgroundToken(requireNotNull(state.accountEmail) { "Connect Google Drive first." })
        return DriveApi(token, onUnauthorized = { graph.authorization.invalidate(token) })
    }
}
