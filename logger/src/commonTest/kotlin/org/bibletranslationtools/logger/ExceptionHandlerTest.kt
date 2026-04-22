package org.bibletranslationtools.logger

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ExceptionHandlerTest {

    @Test
    fun testStacktraceGeneration() {
        val testDir = File(System.getProperty("java.io.tmpdir"), "crash_tests")
        testDir.mkdirs()
        
        val handler = GlobalExceptionHandler(testDir)
        handler.setKillOnException(false) // CRITICAL: Don't kill the test runner!

        val exception = RuntimeException("Test Crash")
        handler.uncaughtException(Thread.currentThread(), exception)

        val files = GlobalExceptionHandler.getStacktraces(testDir)
        assertTrue(files.isNotEmpty(), "A stacktrace file should have been created")
        
        val content = files[0].readText()
        assertTrue(content.contains("RuntimeException: Test Crash"))
        
        testDir.deleteRecursively()
    }
}