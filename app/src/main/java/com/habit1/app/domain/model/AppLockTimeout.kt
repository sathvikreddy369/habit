package com.habit1.app.domain.model

/**
 * Configurable timeout duration for App Lock (Decision 7.1).
 */
enum class AppLockTimeout(val label: String, val durationMs: Long) {
    IMMEDIATELY("Immediately", 0L),
    ONE_MINUTE("1 minute", 60_000L),
    FIVE_MINUTES("5 minutes", 300_000L),
    SCREEN_OFF("When screen turns off", -1L);

    companion object {
        fun fromName(name: String?): AppLockTimeout {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: IMMEDIATELY
        }
    }
}
