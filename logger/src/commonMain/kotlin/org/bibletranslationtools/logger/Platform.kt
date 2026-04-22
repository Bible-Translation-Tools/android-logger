package org.bibletranslationtools.logger

interface PlatformConsole {
    fun print(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
    )
}

expect abstract class PlatformContext

expect fun getDefaultConsole(): PlatformConsole
expect fun getEnvironmentBlock(context: PlatformContext): String
expect fun killProcess()
