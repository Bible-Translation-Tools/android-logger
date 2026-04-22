package org.bibletranslationtools.logger

import java.io.File
import kotlin.test.*

class LoggerTest {

    private lateinit var tempDir: File
    private lateinit var logFile: File

    @BeforeTest
    fun setup() {
        // Create a temp directory for the test run
        tempDir = File(System.getProperty("java.io.tmpdir"), "logger_tests_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        logFile = File(tempDir, "test.log")
        
        // Configure logger for test
        Logger.configure(logFile, LogLevel.Info)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testFileLoggingAndParsing() {
        val tag = "TEST"
        val message = "Hello Kotlin Multiplatform"
        
        // 1. Log a message
        Logger.i(tag, message)
        
        // 2. Verify file exists and has content
        assertTrue(logFile.exists(), "Log file should be created")
        assertTrue(logFile.length() > 0, "Log file should not be empty")
        
        // 3. Parse entries back
        val entries = Logger.getLogEntries()
        assertEquals(1, entries.size)
        assertEquals(tag, entries[0].tag)
        assertEquals(message, entries[0].message)
        assertEquals(LogLevel.Info, entries[0].level)
    }

    @Test
    fun testLogLevelFiltering() {
        // Set min level to Warning
        Logger.configure(logFile, LogLevel.Warning)
        
        Logger.i("SILENT", "This should not be logged to file")
        Logger.w("LOUD", "This should be logged")
        
        val entries = Logger.getLogEntries()
        assertEquals(1, entries.size)
        assertEquals("LOUD", entries[0].tag)
    }

    @Test
    fun testMultiLineDetails() {
        val message = "Main Message"
        val details = "Line 1 of details\nLine 2 of details"
        
        // Mimic a multi-line log entry via the file writer logic
        // (Usually happens via stack traces)
        Logger.e("CRASH", message, RuntimeException("Line 1 of details\nLine 2 of details"))
        
        val entries = Logger.getLogEntries()
        val entry = entries[0]
        
        assertTrue(entry.details.contains("Line 1 of details"))
        assertTrue(entry.details.contains("Line 2 of details"))
    }
}