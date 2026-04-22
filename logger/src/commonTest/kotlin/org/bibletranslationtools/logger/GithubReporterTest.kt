package org.bibletranslationtools.logger

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.*

class GithubReporterTest {

    private val repoUrl = "https://api.github.com/repos/test/repo/issues"
    private val token = "test-token"
    private val context = Context(versionName = "1.0.0", udid = "device-123")
    private val json = Json { ignoreUnknownKeys = true }

    private data class CapturedRequest(val url: String, val authHeader: String?, val body: String)

    private fun makeReporter(
        status: HttpStatusCode,
        onCapture: ((CapturedRequest) -> Unit)? = null
    ): GithubReporter {
        val engine = MockEngine { request ->
            val bodyBytes = (request.body as? OutgoingContent.ByteArrayContent)?.bytes() ?: byteArrayOf()
            onCapture?.invoke(
                CapturedRequest(
                    url = request.url.toString(),
                    authHeader = request.headers[HttpHeaders.Authorization],
                    body = bodyBytes.decodeToString()
                )
            )
            respond(
                content = ByteReadChannel(""),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return GithubReporter(repoUrl, token, context, client)
    }

    private fun bodyField(captured: CapturedRequest): String =
        json.parseToJsonElement(captured.body).jsonObject["body"]!!.jsonPrimitive.content

    private fun titleField(captured: CapturedRequest): String =
        json.parseToJsonElement(captured.body).jsonObject["title"]!!.jsonPrimitive.content

    // --- success / failure ---

    @Test
    fun reportCrashReturnsTrueOn201() = runTest {
        assertTrue(makeReporter(HttpStatusCode.Created).reportCrash("notes", "stacktrace"))
    }

    @Test
    fun reportCrashReturnsFalseOn422() = runTest {
        assertFalse(makeReporter(HttpStatusCode.UnprocessableEntity).reportCrash("notes", "stacktrace"))
    }

    @Test
    fun reportBugReturnsTrueOn201() = runTest {
        assertTrue(makeReporter(HttpStatusCode.Created).reportBug("notes", "log"))
    }

    @Test
    fun reportBugReturnsFalseOn500() = runTest {
        assertFalse(makeReporter(HttpStatusCode.InternalServerError).reportBug("notes", "log"))
    }

    // --- request details ---

    @Test
    fun reportCrashUsesCorrectUrl() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportCrash("notes", "stacktrace")
        assertEquals(repoUrl, captured?.url)
    }

    @Test
    fun reportCrashSendsAuthorizationHeader() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportCrash("notes", "stacktrace")
        assertEquals("token $token", captured?.authHeader)
    }

    // --- body content ---

    @Test
    fun reportCrashBodyContainsNotes() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportCrash("my notes", "stacktrace")
        assertTrue(bodyField(captured!!).contains("my notes"))
    }

    @Test
    fun reportCrashBodyContainsStacktrace() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportCrash("notes", "java.lang.NullPointerException")
        assertTrue(bodyField(captured!!).contains("java.lang.NullPointerException"))
    }

    @Test
    fun reportCrashBodyContainsLogWhenProvided() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportCrash("notes", "stacktrace", "some log content")
        val body = bodyField(captured!!)
        assertTrue(body.contains("some log content"))
        assertTrue(body.contains("Log history"))
    }

    @Test
    fun reportCrashBodyHasNoLogBlockWhenLogNull() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportCrash("notes", "stacktrace", null)
        assertFalse(bodyField(captured!!).contains("Log history"))
    }

    @Test
    fun reportBugBodyContainsNotes() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportBug("bug notes", "log")
        assertTrue(bodyField(captured!!).contains("bug notes"))
    }

    @Test
    fun reportBugBodyContainsLog() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportBug("notes", "error log line")
        assertTrue(bodyField(captured!!).contains("error log line"))
    }

    // --- title generation ---

    @Test
    fun titleIsNotesWhenShorterThan50Chars() = runTest {
        var captured: CapturedRequest? = null
        val shortNotes = "Short bug description"
        makeReporter(HttpStatusCode.Created) { captured = it }.reportBug(shortNotes, "")
        assertEquals(shortNotes, titleField(captured!!))
    }

    @Test
    fun titleIsTruncatedTo50CharsWithEllipsis() = runTest {
        var captured: CapturedRequest? = null
        val longNotes = "This is a very long description that definitely exceeds fifty characters in length"
        makeReporter(HttpStatusCode.Created) { captured = it }.reportBug(longNotes, "")
        val title = titleField(captured!!)
        assertEquals(50, title.length)
        assertTrue(title.endsWith("..."))
    }

    @Test
    fun titleIsDefaultCrashWhenNotesEmpty() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportCrash("", "stacktrace")
        assertEquals("crash report", titleField(captured!!))
    }

    @Test
    fun titleIsDefaultBugWhenNotesEmpty() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportBug("", "")
        assertEquals("bug report", titleField(captured!!))
    }

    // --- file-based overloads ---

    @Test
    fun reportCrashFromFileReadsStacktrace() = runTest {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "reporter_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val stacktraceFile = File(tempDir, "crash.txt").apply { writeText("crash stacktrace content") }
        try {
            var captured: CapturedRequest? = null
            makeReporter(HttpStatusCode.Created) { captured = it }.reportCrash("notes", stacktraceFile)
            assertTrue(bodyField(captured!!).contains("crash stacktrace content"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun reportBugFromFileReadsLog() = runTest {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "reporter_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val logFile = File(tempDir, "app.log").apply { writeText("log file content") }
        try {
            var captured: CapturedRequest? = null
            makeReporter(HttpStatusCode.Created) { captured = it }.reportBug("notes", logFile)
            assertTrue(bodyField(captured!!).contains("log file content"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun reportBugFromNullFileHasNoLogBlock() = runTest {
        var captured: CapturedRequest? = null
        makeReporter(HttpStatusCode.Created) { captured = it }.reportBug("notes", null as File?)
        assertFalse(bodyField(captured!!).contains("Log history"))
    }
}
