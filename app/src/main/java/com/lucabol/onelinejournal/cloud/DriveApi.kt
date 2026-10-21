package com.lucabol.onelinejournal.cloud

import com.lucabol.onelinejournal.core.JournalFormat
import com.lucabol.onelinejournal.core.MAX_BACKUP_BYTES
import com.lucabol.onelinejournal.core.RemoteBackup
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class DriveHttpException(val status: Int, val reason: String?) : IOException(
    when {
        status == 401 -> "Google authorization expired. Reconnect Google Drive."
        reason == "storageQuotaExceeded" -> "Google Drive is full. Free some space and retry."
        status == 403 -> "Google Drive refused access. Check API configuration and account permissions."
        status == 404 -> "A Google Drive backup or folder is missing. No data was overwritten."
        status == 429 || status >= 500 -> "Google Drive is temporarily unavailable. Backup will retry."
        else -> "Google Drive request failed (HTTP $status). No local entries were removed."
    },
)

data class DriveAccount(val email: String, val id: String)
data class DriveFolder(val id: String, val name: String)

class DriveApi(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).build(),
    private val base: HttpUrl = "https://www.googleapis.com/".toHttpUrl(),
    private val onUnauthorized: (suspend () -> Unit)? = null,
) {
    suspend fun account(): DriveAccount {
        val root = jsonRequest("drive/v3/about", parameters = mapOf("fields" to "user(emailAddress,permissionId)"))
        val user = root.getValue("user").jsonObject
        return DriveAccount(user.getValue("emailAddress").jsonPrimitive.content,
            user.getValue("permissionId").jsonPrimitive.content)
    }

    suspend fun folders(): List<DriveFolder> = listFiles(
        "trashed = false and mimeType = 'application/vnd.google-apps.folder' and " +
            "appProperties has { key='oneLineKind' and value='journal-folder' }",
        "files(id,name),nextPageToken",
    ).map { DriveFolder(it.getValue("id").jsonPrimitive.content, it.getValue("name").jsonPrimitive.content) }

    suspend fun createFolder(): DriveFolder {
        val response = jsonRequest("drive/v3/files", method = "POST", parameters = mapOf("fields" to "id,name"),
            body = buildJsonObject {
                put("name", "OneLineJournal")
                put("mimeType", "application/vnd.google-apps.folder")
                put("appProperties", buildJsonObject { put("oneLineKind", "journal-folder") })
            }.toString().toRequestBody(JSON))
        return DriveFolder(response.getValue("id").jsonPrimitive.content, response.getValue("name").jsonPrimitive.content)
    }

    suspend fun backups(folderId: String): List<RemoteBackup> = listFiles(
        "'${escape(folderId)}' in parents and trashed = false and " +
            "appProperties has { key='oneLineKind' and value='snapshot' }",
        "files(id,createdTime,appProperties),nextPageToken",
        order = "createdTime desc",
    ).map { file ->
        val props = file.getValue("appProperties").jsonObject
        RemoteBackup(
            fileId = file.getValue("id").jsonPrimitive.content,
            snapshotId = props.getValue("snapshotId").jsonPrimitive.content,
            parentSnapshotId = props["parentSnapshotId"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            journalId = props.getValue("journalId").jsonPrimitive.content,
            writerId = props.getValue("writerId").jsonPrimitive.content,
            generation = props.getValue("generation").jsonPrimitive.content.toLong(),
            createdAt = file.getValue("createdTime").jsonPrimitive.content,
            encrypted = props.getValue("encrypted").jsonPrimitive.content.toBooleanStrict(),
            sha256 = props.getValue("sha256").jsonPrimitive.content,
        )
    }

    suspend fun download(backup: RemoteBackup): ByteArray {
        val bytes = request("drive/v3/files/${backup.fileId}", parameters = mapOf("alt" to "media"))
        require(digest(bytes) == backup.sha256) { "This backup was changed or damaged outside the app. Choose another backup." }
        return bytes
    }

    suspend fun upload(folderId: String, bytes: ByteArray, metadata: RemoteBackup): RemoteBackup {
        val generated = jsonRequest("drive/v3/files/generateIds",
            parameters = mapOf("count" to "1", "space" to "drive", "type" to "files"))
            .getValue("ids").jsonArray.first().jsonPrimitive.content
        val properties = buildJsonObject {
            put("oneLineKind", "snapshot")
            put("snapshotId", metadata.snapshotId)
            put("parentSnapshotId", metadata.parentSnapshotId.orEmpty())
            put("journalId", metadata.journalId)
            put("writerId", metadata.writerId)
            put("generation", metadata.generation.toString())
            put("encrypted", metadata.encrypted.toString())
            put("sha256", digest(bytes))
        }
        val file = buildJsonObject {
            put("id", generated)
            put("name", "journal-${metadata.createdAt.take(10)}-${metadata.snapshotId}.json")
            put("mimeType", "application/json")
            put("parents", JsonArray(listOf(JsonPrimitive(folderId))))
            put("appProperties", properties)
        }
        val multipart = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(file.toString().toRequestBody(JSON))
            .addPart(bytes.toRequestBody(JSON)).build()
        val result = jsonRequest("upload/drive/v3/files", method = "POST",
            parameters = mapOf("uploadType" to "multipart", "fields" to "id,createdTime"), body = multipart)
        return metadata.copy(fileId = result.getValue("id").jsonPrimitive.content,
            createdAt = result.getValue("createdTime").jsonPrimitive.content, sha256 = digest(bytes))
    }

    suspend fun trash(fileId: String) {
        jsonRequest("drive/v3/files/$fileId", method = "PATCH",
            body = """{"trashed":true}""".toRequestBody(JSON))
    }

    private suspend fun listFiles(query: String, fields: String, order: String? = null): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        var page: String? = null
        do {
            val parameters = mutableMapOf("q" to query, "fields" to fields, "pageSize" to "100", "spaces" to "drive")
            page?.let { parameters["pageToken"] = it }
            order?.let { parameters["orderBy"] = it }
            val response = jsonRequest("drive/v3/files", parameters = parameters)
            results += response.getValue("files").jsonArray.map(JsonElement::jsonObject)
            page = response["nextPageToken"]?.jsonPrimitive?.content
            require(results.size <= 10_000) { "Too many Drive files. No backup was modified." }
        } while (page != null)
        return results
    }

    private suspend fun jsonRequest(
        path: String,
        method: String = "GET",
        parameters: Map<String, String> = emptyMap(),
        body: RequestBody? = null,
    ): JsonObject = JournalFormat.json.parseToJsonElement(request(path, method, parameters, body).toString(Charsets.UTF_8)).jsonObject

    private suspend fun request(
        path: String,
        method: String = "GET",
        parameters: Map<String, String> = emptyMap(),
        body: RequestBody? = null,
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = checkNotNull(base.resolve(path)).newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .method(method, body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) onUnauthorized?.invoke()
                val errorBody = response.body?.byteStream()?.use { stream ->
                    val bytes = ByteArray(16_384)
                    val count = stream.read(bytes)
                    if (count < 0) "" else String(bytes, 0, count, Charsets.UTF_8)
                }.orEmpty()
                val reason = Regex("\"reason\"\\s*:\\s*\"([A-Za-z]+)\"").find(errorBody)?.groupValues?.get(1)
                throw DriveHttpException(response.code, reason)
            }
            val stream = requireNotNull(response.body) { "Google Drive returned an empty response." }
            require(stream.contentLength() <= MAX_BACKUP_BYTES) { "The cloud backup exceeds the size limit." }
            val source = stream.source()
            val buffer = okio.Buffer()
            var total = 0L
            while (true) {
                val read = source.read(buffer, minOf(8192L, MAX_BACKUP_BYTES + 1L - total))
                if (read == -1L) break
                total += read
                require(total <= MAX_BACKUP_BYTES) { "The cloud backup exceeds the size limit." }
            }
            buffer.readByteArray()
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        private fun escape(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
    }
}
