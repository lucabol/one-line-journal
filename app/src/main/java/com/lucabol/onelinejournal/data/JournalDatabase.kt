package com.lucabol.onelinejournal.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import com.lucabol.onelinejournal.core.JournalEntry

@Entity(tableName = "entries")
data class EntryRow(
    @PrimaryKey val journalDate: String,
    val text: String,
    val promptText: String,
    val createdAt: String,
    val updatedAt: String,
    val revision: Long,
    val deletedAt: String?,
) {
    fun entry() = JournalEntry(journalDate, text, promptText, createdAt, updatedAt, revision, deletedAt)

    companion object {
        fun from(entry: JournalEntry) = with(entry) {
            EntryRow(journalDate, text, promptText, createdAt, updatedAt, revision, deletedAt)
        }
    }
}

@Entity(tableName = "journal_state")
data class JournalState(
    @PrimaryKey val id: Int = 1,
    val journalId: String,
    val writerId: String,
    val generation: Long = 0,
    val backedUpGeneration: Long = 0,
    val accountEmail: String? = null,
    val accountId: String? = null,
    val folderId: String? = null,
    val baselineFileId: String? = null,
    val baselineSnapshotId: String? = null,
    val restoreRequired: Boolean = false,
    val lastBackupAt: String? = null,
    val cloudMessage: String = "Google Drive is not connected. Entries are saved only on this phone.",
)

@Dao
interface JournalDao {
    @Query("SELECT * FROM entries ORDER BY journalDate DESC")
    fun observeEntries(): Flow<List<EntryRow>>

    @Query("SELECT * FROM journal_state WHERE id = 1")
    fun observeState(): Flow<JournalState?>

    @Query("SELECT * FROM entries ORDER BY journalDate")
    suspend fun entries(): List<EntryRow>

    @Query("SELECT * FROM entries WHERE journalDate = :date")
    suspend fun entry(date: String): EntryRow?

    @Query("SELECT * FROM journal_state WHERE id = 1")
    suspend fun state(): JournalState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEntry(entry: EntryRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEntries(entries: List<EntryRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: JournalState)

    @Query("DELETE FROM entries")
    suspend fun clearEntries()
}

@Database(entities = [EntryRow::class, JournalState::class], version = 1, exportSchema = true)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
}
