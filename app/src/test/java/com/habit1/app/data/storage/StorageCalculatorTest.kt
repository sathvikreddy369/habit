package com.habit1.app.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageCalculatorTest {

    @Test
    fun formatBytes_formatsVariousByteSizesCorrectly() {
        assertEquals("0 B", StorageCalculator.formatBytes(0L))
        assertEquals("512 B", StorageCalculator.formatBytes(512L))
        assertEquals("1.0 KB", StorageCalculator.formatBytes(1024L))
        assertEquals("250.0 KB", StorageCalculator.formatBytes(256000L))
        assertEquals("1.00 MB", StorageCalculator.formatBytes(1024L * 1024L))
        assertEquals("45.50 MB", StorageCalculator.formatBytes((45.5 * 1024 * 1024).toLong()))
        assertEquals("1.50 GB", StorageCalculator.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
