package org.bibletranslationtools.logger

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class ReporterError(val code: Int, val message: String)

@Serializable
private data class JsonBody(val title: String, val body: String)

private fun defaultClient() = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

class HttpReporter(
    private val url: String,
    private val context: PlatformContext,
    private val client: HttpClient = defaultClient(),
    private val configureRequest: HttpRequestBuilder.(title: String, body: String) -> Unit
) {
    private var _lastError: ReporterError? = null

    fun getLastResponse(): ReporterError? = _lastError

    companion object {
        private const val MAX_TITLE_LENGTH = 50
        private const val DEFAULT_CRASH_TITLE = "crash report"
        private const val DEFAULT_BUG_TITLE = "bug report"

        fun bearer(
            url: String,
            token: String,
            context: PlatformContext,
            client: HttpClient = defaultClient()
        ) = HttpReporter(url, context, client) { title, body ->
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(JsonBody(title, body))
        }

        fun apiKey(
            url: String,
            headerName: String,
            key: String,
            context: PlatformContext,
            client: HttpClient = defaultClient()
        ) = HttpReporter(url, context, client) { title, body ->
            header(headerName, key)
            contentType(ContentType.Application.Json)
            setBody(JsonBody(title, body))
        }

        fun noAuth(
            url: String,
            context: PlatformContext,
            client: HttpClient = defaultClient()
        ) = HttpReporter(url, context, client) { title, body ->
            contentType(ContentType.Application.Json)
            setBody(JsonBody(title, body))
        }
    }

    private suspend fun submit(title: String, body: String): Boolean {
        return runCatching {
            val response = client.post(url) {
                configureRequest(title, body)
            }
            if (!response.status.isSuccess()) {
                _lastError = ReporterError(response.status.value, response.status.description)
                false
            } else {
                _lastError = null
                true
            }
        }.getOrElse { e ->
            _lastError = ReporterError(-1, e.message ?: "Unknown error")
            false
        }
    }

    suspend fun reportCrash(notes: String, stacktraceFile: File, logFile: File? = null): Boolean {
        val stacktrace = stacktraceFile.readText()
        val log = if (logFile?.exists() == true) {
            runCatching { logFile.readText() }.getOrNull()
        } else null
        return reportCrash(notes, stacktrace, log)
    }

    suspend fun reportCrash(notes: String, stacktrace: String, log: String? = null): Boolean {
        val title = getTitle(notes, DEFAULT_CRASH_TITLE)
        val body = buildString {
            append(getNotesBlock(notes))
            append(getEnvironmentBlock(context))
            append(getStacktraceBlock(stacktrace))
            append(getLogBlock(log))
        }
        return submit(title, body)
    }

    suspend fun reportBug(notes: String, logFile: File? = null): Boolean {
        val log = if (logFile?.exists() == true) {
            runCatching { logFile.readText() }.getOrNull()
        } else null
        return reportBug(notes, log ?: "")
    }

    suspend fun reportBug(notes: String, log: String): Boolean {
        val title = getTitle(notes, DEFAULT_BUG_TITLE)
        val body = buildString {
            append(getNotesBlock(notes))
            append(getEnvironmentBlock(context))
            append(getLogBlock(log))
        }
        return submit(title, body)
    }

    private fun getLogBlock(log: String?): String = buildString {
        if (!log.isNullOrEmpty()) {
            append("Log history\n======\n")
            append("```java\n")
            append("$log\n")
            append("```\n")
        }
    }

    private fun getStacktraceBlock(stacktrace: String?): String = buildString {
        if (!stacktrace.isNullOrEmpty()) {
            append("Stack trace\n======\n")
            append("```java\n")
            append("$stacktrace\n")
            append("```\n")
        }
    }

    private fun getNotesBlock(notes: String): String = buildString {
        if (notes.isNotEmpty()) {
            append("Notes\n======\n")
            append("$notes\n")
        }
    }

    private fun getTitle(notes: String, defaultTitle: String): String {
        return when {
            notes.isEmpty() -> defaultTitle
            notes.length < MAX_TITLE_LENGTH -> notes
            else -> notes.take(MAX_TITLE_LENGTH - 3) + "..."
        }
    }
}
