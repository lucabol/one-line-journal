package com.lucabol.onelinejournal.core

data class RemoteBackup(
    val fileId: String,
    val snapshotId: String,
    val parentSnapshotId: String?,
    val journalId: String,
    val writerId: String,
    val generation: Long,
    val createdAt: String,
    val encrypted: Boolean,
    val sha256: String,
)

sealed interface BackupDecision {
    data object Upload : BackupDecision
    data class RecoverUpload(val backup: RemoteBackup) : BackupDecision
    data class Conflict(val message: String) : BackupDecision
}

object BackupRules {
    fun decide(
        local: JournalSnapshot,
        baselineFileId: String?,
        remote: List<RemoteBackup>,
    ): BackupDecision {
        val latest = remote.firstOrNull()
        if (latest == null) return if (baselineFileId == null) BackupDecision.Upload
            else BackupDecision.Conflict("The last Google Drive backup is missing. Restore or reconnect before uploading.")
        if (remote.any { it.journalId != local.journalId }) {
            return BackupDecision.Conflict("This Drive folder contains a different journal. Restore it before uploading.")
        }
        if (latest.fileId == baselineFileId) return BackupDecision.Upload
        val baselineIndex = remote.indexOfFirst { it.fileId == baselineFileId }
        val unacknowledged = if (baselineIndex >= 0) remote.take(baselineIndex) else remote
        val branches = unacknowledged.filter { it.parentSnapshotId != null }.groupBy { it.parentSnapshotId }
            .values.any { children -> children.map { it.snapshotId }.distinct().size > 1 }
        if (branches) return BackupDecision.Conflict("Two backup branches exist. Choose a backup to restore; neither has been overwritten.")
        if (latest.writerId == local.writerId && latest.generation <= local.generation &&
            latest.parentSnapshotId == local.parentSnapshotId) {
            return BackupDecision.RecoverUpload(latest)
        }
        return BackupDecision.Conflict("Google Drive changed on another phone or outside this app. Restore and choose the active phone before uploading.")
    }
}
