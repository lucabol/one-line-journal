package com.lucabol.onelinejournal

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.google.android.gms.common.api.ApiException
import com.lucabol.onelinejournal.cloud.BackupWorker
import com.lucabol.onelinejournal.cloud.CloudBackup
import com.lucabol.onelinejournal.cloud.DriveAuthorization
import com.lucabol.onelinejournal.cloud.DriveHttpException
import com.lucabol.onelinejournal.cloud.ReconnectRequired
import com.lucabol.onelinejournal.core.BackupEncryption
import com.lucabol.onelinejournal.core.BackupLockedException
import com.lucabol.onelinejournal.data.JournalDatabase
import com.lucabol.onelinejournal.data.JournalStore
import com.lucabol.onelinejournal.data.PassphraseVault
import com.lucabol.onelinejournal.data.Settings
import com.lucabol.onelinejournal.reminders.Reminders
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.SerializationException

class JournalApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

class AppGraph(val application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        Log.e("OneLine", "A background journal task failed (${error.javaClass.simpleName}). Pending changes remain on this phone.")
    })
    val database = Room.databaseBuilder(application, JournalDatabase::class.java, "journal.db").build()
    val store = JournalStore(database)
    val settings = Settings(application)
    val vault = PassphraseVault(application)
    val reminders = Reminders(application)
    val authorization = DriveAuthorization(application)
    val cloudMutex = Mutex()
    val cloud = CloudBackup(this)
    val ready = scope.async {
        store.initialize()
        reminders.createChannel()
        reminders.schedule(settings.flow.first())
        BackupWorker.ensurePeriodic(application)
    }

    init {
        scope.launch {
            ready.await()
            store.state.map { state ->
                state?.let { Triple(it.generation, it.folderId, it.restoreRequired) }
            }.distinctUntilChanged().collect {
                val state = store.currentState()
                if (state.folderId != null && !state.restoreRequired && state.generation > state.backedUpGeneration) {
                    BackupWorker.enqueue(application)
                }
            }
        }
    }

    suspend fun safetyCopy() {
        val snapshot = store.snapshot()
        if (snapshot.entries.isEmpty()) return
        val encrypted = settings.flow.first().encryptionEnabled
        val password = if (encrypted) vault.load() else null
        val bytes = try { BackupEncryption.encode(snapshot, password) } finally { password?.fill('\u0000') }
        val folder = File(application.noBackupFilesDir, "recovery").apply {
            check(isDirectory || mkdirs()) { "Could not create a safety-copy folder. Import was not applied." }
        }
        val file = File(folder, "before-restore-${Instant.now().toEpochMilli()}.json")
        try {
            file.outputStream().use { it.write(bytes); it.fd.sync() }
            folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach {
                check(it.delete()) { "Could not prune an old local recovery copy." }
            }
        } finally { bytes.fill(0) }
    }
}

fun userMessage(error: Exception): String = when (error) {
    is BackupLockedException -> error.message.orEmpty()
    is AEADBadTagException -> "The recovery passphrase is incorrect, or this encrypted backup is damaged."
    is ReconnectRequired -> error.message.orEmpty()
    is DriveHttpException -> error.message.orEmpty()
    is ApiException -> "Google authorization failed (${error.statusCode}). Check the Android OAuth client, package name, signing SHA-1, Drive API, and consent status."
    is SerializationException -> "This is not a valid supported journal backup. No entries were changed."
    is DateTimeParseException -> "This backup contains an invalid date or timestamp. No entries were changed."
    is IOException -> "A file or network operation failed. Local entries are safe. Check the destination or connection and retry."
    is IllegalArgumentException, is IllegalStateException -> error.message ?: "The operation could not be completed."
    else -> "The operation failed (${error.javaClass.simpleName}). Local entries were not intentionally removed; retry or restore a backup."
}
