package me.ahoo.cobal.springai

import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.NetworkError
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.ServerError
import me.ahoo.cobal.TimeoutError
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.ConnectException

class SpringAiErrorConverterTest {
    @Test
    fun `should convert 429 to RateLimitError`() {
        val error = RuntimeException("HTTP 429 Too Many Requests")
        val result = SpringAiErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(RateLimitError::class.java)
    }

    @Test
    fun `should convert 401 to AuthenticationError`() {
        val error = RuntimeException("HTTP 401 Unauthorized")
        val result = SpringAiErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(AuthenticationError::class.java)
    }

    @Test
    fun `should convert 500 to ServerError`() {
        val error = RuntimeException("HTTP 500 Internal Server Error")
        val result = SpringAiErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(ServerError::class.java)
    }

    @Test
    fun `should convert timeout message to TimeoutError`() {
        val error = RuntimeException("Connection timeout")
        val result = SpringAiErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(TimeoutError::class.java)
    }

    @Test
    fun `should convert ConnectException to NetworkError`() {
        val error = ConnectException("Connection refused")
        val result = SpringAiErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(NetworkError::class.java)
    }

    @Test
    fun `should traverse cause chain`() {
        val rootCause = RuntimeException("HTTP 429 Rate Limited")
        val wrapper = RuntimeException("Request failed", rootCause)
        val result = SpringAiErrorConverter.convert("node-1", wrapper)
        result.assert().isInstanceOf(RateLimitError::class.java)
    }

    @Test
    fun `should convert unknown error to NodeError`() {
        val error = RuntimeException("Something unexpected")
        val result = SpringAiErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(NodeError::class.java)
    }
}
