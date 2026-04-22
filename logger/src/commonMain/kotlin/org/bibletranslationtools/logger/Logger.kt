package org.bibletranslationtools.logger

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Logs messages using the android Log class and also records logs to a file if configured.
 */
class Logger private constructor(
    private val logFile: File?,
    private val minLoggingLevel: LogLevel = LogLevel.Info,
    private val maxLogFileSize: Long = DEFAULT_MAX_LOG_FILE_SIZE
) {

    private val console: PlatformConsole = getDefaultConsole()
    private var _stacktraceDir: File? = null

    companion object {
        /**
         * The pattern to match the leading log line
         */
        private const val PATTERN = "(\\d+/\\d+/\\d+\\s+\\d+:\\d+\\s+[AP]M)\\s+([A-Z])/(((?!:).)*):\\s*(.*)"
        private const val DEFAULT_MAX_LOG_FILE_SIZE = 1024L * 200

        @Volatile
        private var sInstance: Logger = Logger(null, LogLevel.Info)

        /**
         * Configures the logger to write log messages to a file
         *
         * @param file the file where logs will be written
         * @param level the minimum level a log must be before it is recorded to the log file
         * @param maxSize the maximum size the log file may become before old logs are truncated
         * @param console Platform-specific console
         */
        @JvmStatic
        fun configure(
            file: File,
            level: LogLevel,
            maxSize: Long = DEFAULT_MAX_LOG_FILE_SIZE
        ) {
            sInstance = Logger(file, level, maxSize)
        }

        /**
         * Sends an info message to LogCat and to a log file.
         *
         * @param tag A tag identifying a group of log messages. Should be a constant in the
         * class calling the logger.
         * @param msg The message to add to the log.
         */
        @JvmStatic
        fun i(tag: String, msg: String) {
            sInstance.console.print(LogLevel.Info, tag, msg)
            sInstance.logToFile(LogLevel.Info, tag, msg)
        }

        /**
         * Sends a message and the exception to LogCat and to a log file.
         *
         * @param tag A tag identifying a group of log messages. Should be a constant in the
         * class calling the logger.
         * @param msg The message to add to the log.
         * @param t An exception to log
         */
        @JvmStatic
        fun w(tag: String, msg: String, t: Throwable? = null) {
            sInstance.console.print(LogLevel.Warning, tag, msg, t)

            val fileMsg = if (t != null) "$msg\n${getStackTraceString(t)}" else msg
            sInstance.logToFile(LogLevel.Warning, tag, fileMsg)
        }

        /**
         * Sends an error message to platform console and to a log file.
         *
         * @param tag A tag identifying a group of log messages. Should be a constant in the
         * class calling the logger.
         * @param msg The message to add to the log.
         * @param t Optional throwable to append stacktrace
         */
        @JvmStatic
        fun e(tag: String, msg: String, t: Throwable? = null) {
            sInstance.console.print(LogLevel.Error, tag, msg, t)

            val fileMsg = if (t != null) "$msg\n${getStackTraceString(t)}" else msg
            sInstance.logToFile(LogLevel.Error, tag, fileMsg)
        }

        /**
         * Registers the global exception handler
         * @param stacktraceDir the directory where stacktraces will be stored
         * @param autoKill kills the main process automatically when an exception occurs
         */
        @JvmStatic
        fun registerGlobalExceptionHandler(stacktraceDir: File, autoKill: Boolean = true) {
            if (Thread.getDefaultUncaughtExceptionHandler() !is GlobalExceptionHandler) {
                sInstance._stacktraceDir = stacktraceDir
                val geh = GlobalExceptionHandler(stacktraceDir)
                geh.setKillOnException(autoKill)
                Thread.setDefaultUncaughtExceptionHandler(geh)
            }
        }

        /**
         * Removes the exception handler.
         */
        @JvmStatic
        fun unRegisterGlobalExceptionHandler() {
            sInstance._stacktraceDir = null
            Thread.setDefaultUncaughtExceptionHandler(null)
        }

        /**
         * Returns the stacktrace directory
         * @return
         */
        @JvmStatic
        fun getStacktraceDir(): File? {
            return sInstance._stacktraceDir
        }

        /**
         * Returns an array of stacktrace files found in the directory
         * @return
         */
        @JvmStatic
        fun listStacktraces(): List<File> {
            return sInstance._stacktraceDir?.let {
                GlobalExceptionHandler.getStacktraces(it)
            } ?: emptyList()
        }

        /**
         * Empties the log file and deletes stack traces
         */
        @JvmStatic
        fun flush() {
            sInstance.logFile?.delete()
            sInstance._stacktraceDir?.deleteRecursively()
        }

        /**
         * Returns the path to the current log file
         * @return
         */
        @JvmStatic
        fun getLogFile(): File? {
            return sInstance.logFile
        }

        /**
         * Returns a list of log entries
         * @return
         */
        @JvmStatic
        fun getLogEntries(): List<LogEntry> {
            val currentLogFile = sInstance.logFile
            val logs = mutableListOf<LogEntry>()

            if (currentLogFile != null && currentLogFile.exists()) {
                try {
                    BufferedReader(
                        InputStreamReader(
                            FileInputStream(currentLogFile),
                            "UTF-8"
                        )
                    ).use { br ->
                        val sb = StringBuilder()
                        var line: String?
                        val pattern = Pattern.compile(PATTERN)
                        var currentEntry: LogEntry? = null

                        val dateFormat = SimpleDateFormat("M/d/yy h:mm a", Locale.ENGLISH)

                        while (br.readLine().also { line = it } != null) {
                            if (Thread.interrupted()) break

                            val normalizedLine = line!!.replace(' ', ' ')
                            val match = pattern.matcher(normalizedLine)
                            if (match.find()) {
                                // If we already had an entry being built, save it before starting a new one
                                currentEntry?.let {
                                    logs.add(it.copy(details = sb.toString().trim()))
                                    sb.setLength(0)
                                }

                                // Parse the new log entry
                                try {
                                    val date = dateFormat.parse(match.group(1) ?: "")
                                    val level = LogLevel.getLevel(match.group(2) ?: "I")
                                    val tag = match.group(3) ?: ""
                                    val msg = match.group(5) ?: ""

                                    currentEntry = LogEntry(date, level, tag, msg)
                                } catch (e: Exception) {
                                    // If date parsing fails, treat it as a detail line instead
                                    sb.append(line).append("\n")
                                }
                            } else {
                                // This line doesn't match the pattern, so it's a continuation/detail
                                sb.append(line).append("\n")
                            }
                        }

                        // Add the very last entry
                        currentEntry?.let {
                            logs.add(it.copy(details = sb.toString().trim()))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                sInstance.console.print(
                    LogLevel.Warning,
                    "Logger",
                    "The log file has not been configured or does not exist."
                )
            }
            return logs
        }

        /**
         * Gets a stamp containing the current date and time to write to the log.
         *
         * @return The stamp for the current date and time.
         */
        private fun getDateTimeStamp(): String {
            val dateNow = Calendar.getInstance().time
            return SimpleDateFormat("M/d/yy h:mm a", Locale.ENGLISH).format(dateNow)
        }

        private fun getStackTraceString(t: Throwable): String {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }

    /**
     * Writes a message to the log file on the device.
     *
     * @param level The log level.
     * @param tag A tag identifying a group of log messages.
     * @param message The message to add to the log.
     */
    @Synchronized
    private fun logToFile(level: LogLevel, tag: String, message: String) {
        val file = logFile ?: return
        if (level.ordinal < minLoggingLevel.ordinal) return

        try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            // Append log message
            val existing = file.readText()
            val entry = "${getDateTimeStamp()} ${level.label}/$tag: $message\r\n"
            file.writeText(entry + existing)

            if (file.length() > maxLogFileSize) {
                FileOutputStream(file, true).use { fos ->
                    fos.channel.truncate((maxLogFileSize * 0.8).toLong())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
