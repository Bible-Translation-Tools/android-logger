package org.bibletranslationtools.logger

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer

/**
 * This class writes exceptions to a file on disk before killing the app
 * This allows you to retrieve them later for debugging.
 * http://stackoverflow.com/questions/601503/how-do-i-obtain-crash-data-from-my-android-application
 */
internal class GlobalExceptionHandler(
    private val stacktraceDir: File
) : Thread.UncaughtExceptionHandler {
    private val defaultUEH: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()
    private var killOnException = true

    init {
        if (!stacktraceDir.exists()) {
            stacktraceDir.mkdirs()
        }
    }

    /**
     * Sets whether this class should kill the main process to shut down the app if
     * an exception occurs.
     * @param kill
     */
    fun setKillOnException(kill: Boolean) {
        this.killOnException = kill
    }

    /**
     * Handles the uncaught exception
     * @param t
     * @param e
     */
    override fun uncaughtException(t: Thread, e: Throwable) {
        val tsLong = System.currentTimeMillis()
        val timestamp = tsLong.toString()
        val result: Writer = StringWriter()
        val printWriter = PrintWriter(result)
        e.printStackTrace(printWriter)
        val stacktrace = result.toString()
        printWriter.close()
        val filename = "$timestamp.$STACKTRACE_EXT"

        writeToFile(stacktrace, filename)

        defaultUEH?.uncaughtException(t, e)

        if (killOnException) {
            // force shut down so we don't end up with un-initialized objects
            killProcess()
        }
    }

    /**
     * Writes the stacktrace to the log directory
     * @param stacktrace
     * @param filename
     */
    fun writeToFile(stacktrace: String, filename: String?) {
        try {
            val bos = BufferedWriter(
                FileWriter(stacktraceDir.absolutePath + "/" + filename)
            )
            bos.write(stacktrace)
            bos.flush()
            bos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val STACKTRACE_EXT = "stacktrace"

        /**
         * Returns a list of stacktrace files found in the directory
         * @param stacktraceDir
         * @return
         */
        @JvmStatic
        fun getStacktraces(stacktraceDir: File): List<File> {
            val files = stacktraceDir.listFiles { dir, filename ->
                val file = File(dir, filename)
                file.isFile && file.extension == STACKTRACE_EXT
            }
            return files?.toList() ?: emptyList()
        }
    }
}