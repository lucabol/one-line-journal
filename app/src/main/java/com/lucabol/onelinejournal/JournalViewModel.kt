package com.lucabol.onelinejournal

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.Identity
import com.lucabol.onelinejournal.cloud.BackupWorker
import com.lucabol.onelinejournal.cloud.CloudRestore
import com.lucabol.onelinejournal.cloud.DriveFolder
import com.lucabol.onelinejournal.core.BackupEncryption
import com.lucabol.onelinejournal.core.ConflictPolicy
import com.lucabol.onelinejournal.core.ImportPreview
import com.lucabol.onelinejournal.core.JournalFormat
import com.lucabol.onelinejournal.core.JournalMerge
import com.lucabol.onelinejournal.core.JournalSnapshot
import com.lucabol.onelinejournal.core.MAX_BACKUP_BYTES
import com.lucabol.onelinejournal.core.RemoteBackup
import com.lucabol.onelinejournal.data.JournalSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

sealed interface ImportSource {
    data class Document(val bytes: ByteArray) : ImportSource
    data class Cloud(val backup: RemoteBackup) : ImportSource
}

data class ImportPlan(
    val snapshot: JournalSnapshot,
    val localGeneration: Long,
    val preview: ImportPreview,
    val cloud: CloudRestore? = null,
    val passphrase: CharArray? = null,
)

data class ExportPrompt(val name: String, val readable: Boolean)

class JournalViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val graph = (application as JournalApplication).graph
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    val entries = graph.store.entries.withErrors().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val journalState = graph.store.state.withErrors().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings = graph.settings.flow.withErrors().stateIn(viewModelScope, SharingStarted.Eagerly, JournalSettings())
    val date = savedState.getStateFlow("date", LocalDate.now().toString())
    var pendingAuthorization by mutableStateOf<PendingIntent?>(null)
        private set
    var folderChoices by mutableStateOf<List<DriveFolder>?>(null)
        private set
    var backupChoices by mutableStateOf<List<RemoteBackup>?>(null)
        private set
    var recoveryChoices by mutableStateOf<List<File>?>(null)
        private set
    var lockedImport by mutableStateOf<ImportSource?>(null)
        private set
    var importPlan by mutableStateOf<ImportPlan?>(null)
        private set
    var exportPrompt by mutableStateOf<ExportPrompt?>(null)
        private set
    var requestedDate by mutableStateOf<LocalDate?>(null)
        private set
    private val operationMutex = Mutex()
    private var connectionToken: String? = null
    private var exportBytes: ByteArray? = null

    init { operation { graph.ready.await() } }

    fun selectDate(value: LocalDate) {
        if (savedState.get<String>("date") != value.toString()) {
            listOf("draft_date", "draft_revision", "draft_text", "draft_variation").forEach { savedState.remove<Any>(it) }
        }
        savedState["date"] = value.toString()
    }
    fun draftText(date: String, revision: Long?, fallback: String): String =
        if (savedState.get<String>("draft_date") == date && savedState.get<Long>("draft_revision") == (revision ?: -1))
            savedState.get<String>("draft_text") ?: fallback else fallback

    fun draftVariation(date: String, revision: Long?): Int =
        if (savedState.get<String>("draft_date") == date && savedState.get<Long>("draft_revision") == (revision ?: -1))
            savedState.get<Int>("draft_variation") ?: 0 else 0

    fun keepDraft(date: String, revision: Long?, text: String, variation: Int) {
        savedState["draft_date"] = date
        savedState["draft_revision"] = revision ?: -1L
        savedState["draft_text"] = text
        savedState["draft_variation"] = variation
    }
    fun requestDate(value: LocalDate) { requestedDate = value }
    fun dateRequestHandled() { requestedDate = null }
    fun clearError() { error = null }
    fun reportFailure(exception: Exception) { error = userMessage(exception) }
    fun message(value: String) { notice = value }
    fun clearNotice() { notice = null }

    fun save(date: LocalDate, text: String, prompt: String, revision: Long?) = operation {
        graph.store.save(date, text, prompt, revision)
    }

    fun delete(date: String, revision: Long) = operation {
        graph.store.delete(date, revision)
        notice = "Entry deleted. Older backup snapshots may still contain it."
    }

    fun reminders(active: Boolean, minute: Int, suppress: Boolean) = operation {
        graph.settings.reminders(active, minute, suppress)
        graph.reminders.schedule(graph.settings.flow.first())
    }

    fun testReminder() = operation {
        require(graph.reminders.allowed()) { "Allow journal notifications in Android Settings first." }
        graph.reminders.show(LocalDate.now())
        notice = "Test reminder posted."
    }

    fun setAppLock(enabled: Boolean) = operation { graph.settings.appLock(enabled) }

    fun encryption(passphrase: String?) = operation {
        graph.cloudMutex.withLock {
            if (passphrase != null) {
                val chars = passphrase.toCharArray()
                try { graph.vault.save(chars) } finally { chars.fill('\u0000') }
                graph.settings.encryption(true)
            } else {
                graph.settings.encryption(false)
                graph.vault.clear()
            }
            graph.store.markPending()
            notice = if (passphrase == null) "New backups will be unencrypted. Older encrypted snapshots remain."
                else "New backups will be encrypted. Keep your recovery passphrase somewhere safe."
        }
    }

    fun connect() = operation {
        val result = graph.authorization.authorize(graph.store.currentState().accountEmail)
        if (result.hasResolution()) pendingAuthorization = requireNotNull(result.pendingIntent)
        else finishConnection(requireNotNull(result.accessToken) { "Google did not return a Drive access token." })
    }

    fun authorizationLaunched() { pendingAuthorization = null }

    fun authorizationResult(data: Intent?) = operation {
        requireNotNull(data) { "Google Drive connection was cancelled." }
        val result = Identity.getAuthorizationClient(getApplication<Application>()).getAuthorizationResultFromIntent(data)
        finishConnection(requireNotNull(result.accessToken) { "Drive permission was not granted." })
    }

    private suspend fun finishConnection(token: String, folder: DriveFolder? = null) {
        val choices = graph.cloud.connect(token, folder)
        if (choices.isNotEmpty()) {
            connectionToken = token
            folderChoices = choices
        } else {
            connectionToken = null
            folderChoices = null
            BackupWorker.enqueue(getApplication())
            notice = "Google Drive connected. Existing backups must be restored before this phone can replace their history."
        }
    }

    fun chooseFolder(folder: DriveFolder) = operation {
        finishConnection(requireNotNull(connectionToken) { "The connection expired. Connect Google Drive again." }, folder)
    }

    fun cancelFolder() { connectionToken = null; folderChoices = null }
    fun disconnect() = operation { graph.cloud.disconnect() }
    fun backupNow() = operation {
        require(graph.store.currentState().folderId != null) { "Connect Google Drive first." }
        require(!graph.store.currentState().restoreRequired) { "Preview and restore the existing Drive journal before uploading." }
        graph.store.markPending()
        BackupWorker.enqueue(getApplication())
        notice = "Backup queued. It will run when Android allows network work."
    }

    fun listBackups() = operation { backupChoices = graph.cloud.list() }
    fun cancelBackups() { backupChoices = null }

    fun chooseBackup(backup: RemoteBackup) = operation {
        backupChoices = null
        if (backup.encrypted) lockedImport = ImportSource.Cloud(backup)
        else prepare(ImportSource.Cloud(backup), null)
    }

    fun importDocument(uri: Uri) = operation {
        val bytes = requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)) {
            "Could not open the selected backup."
        }.use(::readBounded)
        val source = ImportSource.Document(bytes)
        if (BackupEncryption.isEncrypted(bytes)) lockedImport = source else prepare(source, null)
    }

    fun localRecovery() = operation {
        recoveryChoices = File(getApplication<Application>().noBackupFilesDir, "recovery")
            .listFiles()?.filter { it.isFile && it.extension == "json" }?.sortedByDescending { it.lastModified() }.orEmpty()
    }

    fun cancelRecovery() { recoveryChoices = null }

    fun chooseRecovery(file: File) = operation {
        recoveryChoices = null
        val source = ImportSource.Document(file.inputStream().use(::readBounded))
        if (BackupEncryption.isEncrypted(source.bytes)) lockedImport = source else prepare(source, null)
    }

    fun unlockImport(passphrase: String) = operation {
        val source = requireNotNull(lockedImport)
        val chars = passphrase.toCharArray()
        try { prepare(source, chars) } finally { chars.fill('\u0000') }
        lockedImport = null
    }

    private suspend fun prepare(source: ImportSource, passphrase: CharArray?) {
        val cloud = if (source is ImportSource.Cloud) graph.cloud.preview(source.backup, passphrase) else null
        val snapshot = when (source) {
            is ImportSource.Document -> BackupEncryption.decode(source.bytes, passphrase)
            is ImportSource.Cloud -> requireNotNull(cloud).snapshot
        }
        val local = graph.store.snapshot()
        importPlan = ImportPlan(snapshot, local.generation, JournalMerge.preview(local.entries, snapshot.entries),
            cloud, passphrase?.copyOf())
        if (source is ImportSource.Document) source.bytes.fill(0)
    }

    fun cancelImport() {
        (lockedImport as? ImportSource.Document)?.bytes?.fill(0)
        lockedImport = null
        importPlan?.passphrase?.fill('\u0000')
        importPlan = null
        error = null
    }

    fun applyImport(policy: ConflictPolicy, replaceAll: Boolean) = operation {
        val plan = requireNotNull(importPlan)
        if (plan.cloud != null) {
            graph.cloud.restore(plan.cloud, plan.localGeneration, policy, replaceAll, plan.passphrase)
        } else {
            graph.cloudMutex.withLock {
                require(graph.store.currentState().generation == plan.localGeneration) { "The journal changed. Preview the file again." }
                graph.safetyCopy()
                graph.store.importSnapshot(plan.snapshot, plan.localGeneration, policy, replaceAll)
            }
        }
        plan.passphrase?.fill('\u0000')
        importPlan = null
        BackupWorker.enqueue(getApplication())
        notice = "Journal imported. A local recovery copy was kept; new Drive backup is pending."
    }

    fun prepareExport(readable: Boolean) = operation {
        val snapshot = graph.store.snapshot()
        exportBytes?.fill(0)
        exportBytes = if (readable) JournalFormat.readable(snapshot).toByteArray() else {
            val password = if (graph.settings.flow.first().encryptionEnabled) graph.vault.load() else null
            try { BackupEncryption.encode(snapshot, password) } finally { password?.fill('\u0000') }
        }
        exportPrompt = ExportPrompt("one-line-${LocalDate.now()}.${if (readable) "txt" else "json"}", readable)
    }

    fun exportLaunched() { exportPrompt = null }
    fun cancelExport() { exportBytes?.fill(0); exportBytes = null; exportPrompt = null }

    fun writeExport(uri: Uri) = operation {
        val bytes = requireNotNull(exportBytes) { "The app restarted before export finished. Start the export again." }
        try {
            requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")) {
                "Could not open the export destination."
            }.use { it.write(bytes); it.flush() }
            notice = "Export saved to the location you chose."
        } finally { cancelExport() }
    }

    private fun operation(block: suspend () -> Unit) {
        viewModelScope.launch {
            operationMutex.withLock {
                busy = true
                error = null
                try {
                    graph.ready.await()
                    withContext(Dispatchers.IO) { block() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = userMessage(e)
                } finally { busy = false }
            }
        }
    }

    private fun <T> Flow<T>.withErrors(): Flow<T> = catch { failure ->
        if (failure is CancellationException) throw failure
        if (failure is Exception) error = userMessage(failure) else throw failure
    }

    override fun onCleared() {
        cancelExport()
        cancelImport()
        super.onCleared()
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BACKUP_BYTES) { "The backup exceeds the 32 MB limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
