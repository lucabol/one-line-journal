package com.lucabol.onelinejournal.core

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

const val MAX_ENTRY_LENGTH = 1_000
const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

@Serializable
data class JournalEntry(
    val journalDate: String,
    val text: String,
    val promptText: String,
    val createdAt: String,
    val updatedAt: String,
    val revision: Long = 1,
    val deletedAt: String? = null,
) {
    fun validate() {
        val date = LocalDate.parse(journalDate)
        require(date.year in 1..9999 && date.toString() == journalDate) { "Invalid calendar date: $journalDate" }
        require(text.length <= MAX_ENTRY_LENGTH) { "An entry exceeds $MAX_ENTRY_LENGTH characters." }
        require(!text.contains('\n') && !text.contains('\r')) { "Each entry must be a single short line." }
        require(promptText.length <= 1_000) { "A writing prompt is too long." }
        require(deletedAt != null || text.isNotBlank()) { "An entry is empty." }
        require(revision > 0) { "Invalid entry revision." }
        require(!Instant.parse(updatedAt).isBefore(Instant.parse(createdAt))) { "An entry's timestamps are inconsistent." }
        deletedAt?.let(Instant::parse)
    }

    fun sameContent(other: JournalEntry): Boolean =
        text == other.text && promptText == other.promptText &&
            (deletedAt == null) == (other.deletedAt == null)
}

@Serializable
data class JournalSnapshot(
    val format: String = "one-line-journal",
    val schemaVersion: Int = 1,
    val journalId: String,
    val writerId: String,
    val snapshotId: String = UUID.randomUUID().toString(),
    val parentSnapshotId: String? = null,
    val generation: Long,
    val exportedAt: String,
    val entries: List<JournalEntry>,
) {
    fun validate() {
        require(format == "one-line-journal" && schemaVersion == 1) { "Unsupported journal backup format or version." }
        listOf(journalId, writerId, snapshotId).forEach { UUID.fromString(it) }
        parentSnapshotId?.let(UUID::fromString)
        require(generation >= 0) { "Invalid backup generation." }
        Instant.parse(exportedAt)
        require(entries.size <= 100_000) { "This backup has too many entries." }
        require(entries.map { it.journalDate }.toSet().size == entries.size) { "The backup contains duplicate calendar dates." }
        entries.forEach(JournalEntry::validate)
    }
}

object JournalFormat {
    val json = Json { encodeDefaults = true; prettyPrint = true; ignoreUnknownKeys = true }

    fun encode(snapshot: JournalSnapshot): ByteArray {
        snapshot.validate()
        return json.encodeToString(JournalSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
            .also { require(it.size <= MAX_BACKUP_BYTES) { "The journal exceeds the backup size limit." } }
    }

    fun decode(bytes: ByteArray): JournalSnapshot {
        require(bytes.size <= MAX_BACKUP_BYTES) { "The backup exceeds the 32 MB limit." }
        val text = utf8(bytes)
        val root = json.parseToJsonElement(text).jsonObject
        require(root["format"]?.jsonPrimitive?.content == "one-line-journal" &&
            root["schemaVersion"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported journal backup format or version." }
        return json.decodeFromString(JournalSnapshot.serializer(), text).also(JournalSnapshot::validate)
    }

    fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun readable(snapshot: JournalSnapshot): String = buildString {
        appendLine("One Line Journal")
        appendLine("Exported ${snapshot.exportedAt}")
        snapshot.entries.filter { it.deletedAt == null }.sortedBy { it.journalDate }.forEach {
            appendLine()
            appendLine("${it.journalDate}: ${it.text}")
            appendLine("Prompt: ${it.promptText}")
        }
    }

    fun onDay(entries: List<JournalEntry>, day: MonthDay): List<JournalEntry> =
        entries.filter { it.deletedAt == null && MonthDay.from(LocalDate.parse(it.journalDate)) == day }
            .sortedByDescending { it.journalDate }
}

enum class ConflictPolicy { KEEP_LOCAL, USE_BACKUP }

data class ImportPreview(
    val newDates: Int,
    val conflictDates: List<String>,
    val existingDates: Int,
)

object JournalMerge {
    fun preview(local: List<JournalEntry>, incoming: List<JournalEntry>): ImportPreview {
        val byDate = local.associateBy { it.journalDate }
        return ImportPreview(
            newDates = incoming.count { it.journalDate !in byDate },
            conflictDates = incoming.filter { entry ->
                byDate[entry.journalDate]?.let { !it.sameContent(entry) } == true
            }.map { it.journalDate }.sorted(),
            existingDates = incoming.count { it.journalDate in byDate },
        )
    }

    fun merge(local: List<JournalEntry>, incoming: List<JournalEntry>, policy: ConflictPolicy): List<JournalEntry> {
        (local + incoming).forEach(JournalEntry::validate)
        val result = local.associateBy { it.journalDate }.toMutableMap()
        incoming.forEach { entry ->
            val old = result[entry.journalDate]
            if (old == null || policy == ConflictPolicy.USE_BACKUP) result[entry.journalDate] = entry
        }
        return result.values.sortedBy { it.journalDate }
    }
}
