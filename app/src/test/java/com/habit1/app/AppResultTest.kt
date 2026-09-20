package com.habit1.app

import com.habit1.app.core.util.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun testSuccessResult() {
        val result: AppResult<String> = AppResult.Success("test_data")
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertEquals("test_data", result.getOrNull())
    }

    @Test
    fun testErrorResult() {
        val exception = IllegalArgumentException("invalid")
        val result: AppResult<String> = AppResult.Error(exception, "Custom error")
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
        assertEquals("Custom error", (result as AppResult.Error).message)
        assertEquals(exception, result.exception)
    }
}
