package me.ahoo.cobal.springai

import me.ahoo.cobal.error.AuthenticationError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.error.NetworkError
import me.ahoo.cobal.error.NodeError
import me.ahoo.cobal.error.RateLimitError
import me.ahoo.cobal.error.ServerError
import me.ahoo.cobal.error.TimeoutError
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class SpringAiNodeErrorConverterTest {

    @Test
    fun `convert 429 to RateLimitError`() {
        val error = RuntimeException("HTTP 429 Too Many Requests")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(RateLimitError::class.java)
        result.nodeId.assert().isEqualTo("node-1")
    }

    @Test
    fun `convert 401 to AuthenticationError`() {
        val error = RuntimeException("HTTP 401 Unauthorized")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(AuthenticationError::class.java)
    }

    @Test
    fun `convert 403 to AuthenticationError`() {
        val error = RuntimeException("HTTP 403 Forbidden")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(AuthenticationError::class.java)
    }

    @Test
    fun `convert 400 to InvalidRequestError`() {
        val error = RuntimeException("HTTP 400 Bad Request")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(InvalidRequestError::class.java)
    }

    @Test
    fun `convert 500 to ServerError`() {
        val error = RuntimeException("HTTP 500 Internal Server Error")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(ServerError::class.java)
    }

    @Test
    fun `convert 502 to ServerError`() {
        val error = RuntimeException("Status 502 Bad Gateway")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(ServerError::class.java)
    }

    @Test
    fun `convert 503 to ServerError`() {
        val error = RuntimeException("503: Service Unavailable")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(ServerError::class.java)
    }

    @Test
    fun `convert timeout message to TimeoutError`() {
        val error = RuntimeException("Connection timeout")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(TimeoutError::class.java)
    }

    @Test
    fun `convert ConnectException to NetworkError`() {
        val error = ConnectException("Connection refused")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(NetworkError::class.java)
    }

    @Test
    fun `convert SocketTimeoutException to NetworkError`() {
        val error = SocketTimeoutException("read timed out")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(NetworkError::class.java)
    }

    @Test
    fun `convert wrapped cause recursively`() {
        val rootCause = RuntimeException("HTTP 429 Rate Limited")
        val wrapper = RuntimeException("Request failed", rootCause)
        val result = SpringAiNodeErrorConverter.convert("node-1", wrapper)
        result.assert().isInstanceOf(RateLimitError::class.java)
    }

    @Test
    fun `convert unknown error to generic NodeError`() {
        val error = IllegalStateException("Something unexpected")
        val result = SpringAiNodeErrorConverter.convert("node-1", error)
        result.assert().isInstanceOf(NodeError::class.java)
        result.assert().isNotInstanceOf(RateLimitError::class.java)
    }
}
