package org.bibletranslationtools.logger

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class GithubReporter(
    private val repositoryUrl: String,
    private val githubOauth2Token: String,
    private val context: PlatformContext,
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) {

    @Serializable
    private data class GithubIssue(val title: String, val body: String)

    companion object {
        private const val MAX_TITLE_LENGTH = 50
        private const val DEFAULT_CRASH_TITLE = "crash report"
        private const val DEFAULT_BUG_TITLE = "bug report"
    }

    private suspend fun submit(title: String, body: String): HttpResponse {
        return client.post(repositoryUrl) {
            header(HttpHeaders.Authorization, "token $githubOauth2Token")
            contentType(ContentType.Application.Json)
            setBody(GithubIssue(title, body))
        }
    }

    /**
     * Creates a crash issue on GitHub.
     */
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

        val response = submit(title, body)
        return response.status.isSuccess()
    }

    /**
     * Creates a bug issue on GitHub.
     * Combined Java overloads using nullable/default arguments.
     */
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

        val response = submit(title, body)
        return response.status.isSuccess()
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