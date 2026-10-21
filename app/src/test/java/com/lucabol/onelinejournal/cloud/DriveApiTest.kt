package com.lucabol.onelinejournal.cloud

import com.lucabol.onelinejournal.core.MAX_BACKUP_BYTES
import com.lucabol.onelinejournal.core.RemoteBackup
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DriveApi

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        api = DriveApi("test-token", base = server.url("/"))
    }
    @After fun close() { server.shutdown() }

    @Test fun `account access is scoped through authenticated REST and returns selected identity`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user":{"emailAddress":"test@example.invalid","permissionId":"account-id"}}"""))
        assertEquals(DriveAccount("test@example.invalid", "account-id"), api.account())
        val request = server.takeRequest()
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertTrue(request.path!!.startsWith("/drive/v3/about?"))
    }

    @Test fun `listing folders follows pagination instead of creating duplicate destination`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"one","name":"OneLineJournal"}],"nextPageToken":"second"}"""))
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"two","name":"OneLineJournal"}]}"""))
        assertEquals(listOf("one", "two"), api.folders().map { it.id })
        assertEquals("GET", server.takeRequest().method)
        assertTrue(server.takeRequest().path!!.contains("pageToken=second"))
    }

    @Test fun `small authorization error is reported instead of mistaken for EOF`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"errors":[{"reason":"authError"}]}}"""))
        val failure = runCatching { api.account() }.exceptionOrNull()
        assertTrue(failure is DriveHttpException)
        assertEquals(401, (failure as DriveHttpException).status)
        assertTrue(failure.message!!.contains("Reconnect"))
    }

    @Test fun `unauthorized responses invalidate cached authorization before requesting reconnect`() = runBlocking {
        var invalidated = false
        val invalidatingApi = DriveApi("expired-token", base = server.url("/"), onUnauthorized = { invalidated = true })
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertTrue(runCatching { invalidatingApi.account() }.exceptionOrNull() is DriveHttpException)
        assertTrue(invalidated)
    }

    @Test fun `download verifies content hash and rejects tampering`() = runBlocking {
        server.enqueue(MockResponse().setBody("original"))
        assertEquals("original", api.download(reference(DriveApi.digest("original".toByteArray()))).toString(Charsets.UTF_8))
        server.enqueue(MockResponse().setBody("changed"))
        assertTrue(runCatching { api.download(reference(DriveApi.digest("original".toByteArray()))) }.isFailure)
    }

    @Test fun `oversized response is rejected without buffering it`() = runBlocking {
        server.enqueue(MockResponse().setBody("x").setHeader("Content-Length", MAX_BACKUP_BYTES + 1))
        assertTrue(runCatching { api.download(reference("irrelevant")) }.isFailure)
    }

    @Test fun `upload creates immutable file with preallocated ID rather than overwriting another snapshot`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ids":["allocated-id"]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"allocated-id","createdTime":"2026-10-19T20:00:00Z"}"""))
        val reference = reference("digest")
        val uploaded = api.upload("folder", """{"format":"test"}""".toByteArray(), reference)
        assertEquals("allocated-id", uploaded.fileId)
        assertTrue(server.takeRequest().path!!.contains("generateIds"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.startsWith("/upload/drive/v3/files?"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("allocated-id"))
        assertTrue(body.contains("snapshotId"))
        assertTrue(body.contains("sha256"))
        assertFalse(body.contains("trashed"))
    }

    private fun reference(hash: String) = RemoteBackup(
        "file", UUID.randomUUID().toString(), null, UUID.randomUUID().toString(),
        UUID.randomUUID().toString(), 1, "2026-10-19T20:00:00Z", false, hash,
    )
}
