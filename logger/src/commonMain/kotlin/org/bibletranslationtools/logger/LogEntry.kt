package org.bibletranslationtools.logger

import java.util.Date

data class LogEntry(
    val date: Date,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String = ""
)
