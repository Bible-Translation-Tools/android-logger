package org.bibletranslationtools.logger

import kotlin.system.exitProcess

class JvmConsole : PlatformConsole {
    override fun print(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        val stream = if (level == LogLevel.Error) System.err else System.out
        stream.println("[${level.label}] $tag: $message")
        throwable?.printStackTrace(stream)
    }
}

abstract class JvmContext {
    abstract val versionName: String
    abstract val udid: String
}

data class Context(
    override val versionName: String,
    override val udid: String
) : JvmContext()

actual typealias PlatformContext = JvmContext

actual fun getEnvironmentBlock(context: PlatformContext): String = buildString {
    append("\nEnvironment\n======\n")
    append("Environment Key | Value\n")
    append(":----: | :----:\n")

    append("version | ${context.versionName}\n")
    append("UDID | ${context.udid}\n")
    append("OS Name | ${System.getProperty("os.name")}\n")
    append("OS Version | ${System.getProperty("os.version")}\n")
    append("Architecture | ${System.getProperty("os.arch")}\n")
    append("Java Version | ${System.getProperty("java.version")}\n")
}

actual fun getDefaultConsole(): PlatformConsole = JvmConsole()
actual fun killProcess() {
    exitProcess(0)
}