package com.habit1.app.data.storage

import android.content.Context
import java.io.File

/**
 * Representation of local storage footprint accurately measured from disk.
 * Strictly avoids double-counting or fictitious estimates.
 */
data class AppStorageUsage(
    val databaseBytes: Long,
    val walBytes: Long,
    val preferencesBytes: Long,
    val cacheBytes: Long,
    val totalAppBytes: Long
) {
    val databaseFormatted: String get() = formatBytes(databaseBytes + walBytes)
    val preferencesFormatted: String get() = formatBytes(preferencesBytes)
    val cacheFormatted: String get() = formatBytes(cacheBytes)
    val totalFormatted: String get() = formatBytes(totalAppBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(java.util.Locale.US, "%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format(java.util.Locale.US, "%.2f GB", gb)
        }
    }
}

/**
 * Accurately measures physical file sizes for Habit1's local SQLite database (including WAL & SHM),
 * DataStore preferences, and temporary cache.
 */
object StorageCalculator {

    fun formatBytes(bytes: Long): String = AppStorageUsage.formatBytes(bytes)

    fun calculateStorageUsage(context: Context): AppStorageUsage {
        var dbBytes = 0L
        var walBytes = 0L
        var prefBytes = 0L
        var cacheBytes = 0L

        // 1. Measure database files (Room habit_app.db, habit_app.db-wal, habit_app.db-shm)
        val dbFile = context.getDatabasePath("habit_app.db")
        if (dbFile != null && dbFile.exists()) {
            dbBytes += dbFile.length()
        }

        val dbDir = dbFile?.parentFile
        if (dbDir != null && dbDir.exists() && dbDir.isDirectory) {
            val walFile = File(dbDir, "habit_app.db-wal")
            if (walFile.exists()) walBytes += walFile.length()

            val shmFile = File(dbDir, "habit_app.db-shm")
            if (shmFile.exists()) walBytes += shmFile.length()
        }

        // 2. Measure preferences / datastore files
        val filesDir = context.filesDir
        val datastoreDir = File(filesDir, "datastore")
        if (datastoreDir.exists() && datastoreDir.isDirectory) {
            datastoreDir.listFiles()?.forEach { file ->
                prefBytes += file.length()
            }
        }

        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
            sharedPrefsDir.listFiles()?.forEach { file ->
                prefBytes += file.length()
            }
        }

        // 3. Measure cache
        val cacheDir = context.cacheDir
        if (cacheDir != null && cacheDir.exists()) {
            cacheBytes += calculateDirSize(cacheDir)
        }

        val total = dbBytes + walBytes + prefBytes + cacheBytes

        return AppStorageUsage(
            databaseBytes = dbBytes,
            walBytes = walBytes,
            preferencesBytes = prefBytes,
            cacheBytes = cacheBytes,
            totalAppBytes = total
        )
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }
}
