package me.ahoo.cobal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorConverterTest {

    @Test
    fun `ErrorConverter returns null when cannot convert`() {
        val converter = ErrorConverter { nodeId, error ->
            error as? RateLimitError
        }
        val cause = RuntimeException("unknown")
        assertNull(converter.convert("node-1", cause))
    }

    @Test
    fun `ErrorConverter converts to CobalError`() {
        val converter = ErrorConverter { nodeId, error ->
            when (error) {
                is RuntimeException -> RateLimitError(nodeId, error)
                else -> null
            }
        }
        val cause = RuntimeException("rate limited")
        val result = converter.convert("node-1", cause)
        assertEquals("node-1", (result as? NodeError)?.nodeId)
    }

    @Test
    fun `DefaultErrorConverter returns null for all throwables`() {
        val converter = ErrorConverter.Default
        val cause = RuntimeException("test")
        assertNull(converter.convert("node-1", cause))
    }
}
