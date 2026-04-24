package me.ahoo.cobal.error

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class ErrorConverterTest {

    @Test
    fun `ErrorConverter returns NodeError when cannot convert`() {
        val converter = ErrorConverter { nodeId, error ->
            error as? RateLimitError ?: NodeError(nodeId, error.message, error)
        }
        val cause = RuntimeException("unknown")
        val result = converter.convert("node-1", cause)
        result.assert().isInstanceOf(NodeError::class.java)
        (result as NodeError).nodeId.assert().isEqualTo("node-1")
    }

    @Test
    fun `ErrorConverter converts to CobalError`() {
        val converter = ErrorConverter { nodeId, error ->
            when (error) {
                is RuntimeException -> RateLimitError(nodeId, error)
                else -> NodeError(nodeId, error.message, error)
            }
        }
        val cause = RuntimeException("rate limited")
        val result = converter.convert("node-1", cause)
        (result as? NodeError)?.nodeId.assert().isEqualTo("node-1")
    }

    @Test
    fun `DefaultErrorConverter returns NodeError for all throwables`() {
        val converter = ErrorConverter.Default
        val cause = RuntimeException("test")
        val result = converter.convert("node-1", cause)
        result.assert().isInstanceOf(NodeError::class.java)
        (result as NodeError).nodeId.assert().isEqualTo("node-1")
    }
}
