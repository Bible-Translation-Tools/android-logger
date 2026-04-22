package org.bibletranslationtools.logger

enum class LogLevel(val label: String) {
    Info("I"),
    Warning("W"),
    Error("E");

    companion object {
        fun getLevel(label: String) =
            LogLevel.entries.find { it.label == label } ?: Info

        fun getLevel(ordinal: Int) =
            LogLevel.entries.getOrNull(ordinal) ?: Info
    }
}