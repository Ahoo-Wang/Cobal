package me.ahoo.cobal.error

import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class NodeErrorTest {

    @Test
    fun `NodeError should hold nodeId`() {
        val cause = RuntimeException("root")
        val error = NodeError("node-1", "something failed", cause)
        error.nodeId.assert().isEqualTo("node-1")
        error.message.assert().isEqualTo("something failed")
        error.cause.assert().isEqualTo(cause)
    }

    @Test
    fun `RateLimitError is retriable`() {
        val error = RateLimitError("node-1", null)
        error.assert().isInstanceOf(RetriableError::class.java)
        error.message.assert().isEqualTo("Rate limited [node-1]")
    }

    @Test
    fun `ServerError is retriable`() {
        val error = ServerError("node-2", null)
        error.assert().isInstanceOf(RetriableError::class.java)
        error.message.assert().isEqualTo("Server error [node-2]")
    }

    @Test
    fun `TimeoutError is retriable`() {
        val error = TimeoutError("node-3", null)
        error.assert().isInstanceOf(RetriableError::class.java)
        error.message.assert().isEqualTo("Timeout [node-3]")
    }

    @Test
    fun `NetworkError is retriable`() {
        val error = NetworkError("node-4", null)
        error.assert().isInstanceOf(RetriableError::class.java)
        error.message.assert().isEqualTo("Network error [node-4]")
    }

    @Test
    fun `AuthenticationError is not retriable`() {
        val error = AuthenticationError("node-5", null)
        error.assert().isNotInstanceOf(RetriableError::class.java)
    }

    @Test
    fun `InvalidRequestError is not retriable`() {
        val error = InvalidRequestError("node-6", null)
        error.assert().isNotInstanceOf(RetriableError::class.java)
    }

    @Test
    fun `isInvalidRequest should return true for InvalidRequestError`() {
        val error = InvalidRequestError("node-1", null)
        error.isInvalidRequest.assert().isTrue()
    }

    @Test
    fun `isInvalidRequest should return false for other NodeError types`() {
        RateLimitError("node-1", null).isInvalidRequest.assert().isFalse()
        ServerError("node-1", null).isInvalidRequest.assert().isFalse()
        TimeoutError("node-1", null).isInvalidRequest.assert().isFalse()
        NetworkError("node-1", null).isInvalidRequest.assert().isFalse()
        AuthenticationError("node-1", null).isInvalidRequest.assert().isFalse()
    }

    @Test
    fun `throwIfInvalidRequest should throw for InvalidRequestError`() {
        val error = InvalidRequestError("node-1", null)
        assertThrownBy<InvalidRequestError> {
            error.throwIfInvalidRequest()
        }
    }

    @Test
    fun `throwIfInvalidRequest should not throw for retriable errors`() {
        RateLimitError("node-1", null).throwIfInvalidRequest()
        ServerError("node-1", null).throwIfInvalidRequest()
        TimeoutError("node-1", null).throwIfInvalidRequest()
        NetworkError("node-1", null).throwIfInvalidRequest()
    }

    @Test
    fun `AuthenticationError should have correct message format`() {
        val error = AuthenticationError("node-1", null)
        error.message.assert().isEqualTo("Auth failed [node-1]")
    }

    @Test
    fun `InvalidRequestError should have correct message format`() {
        val error = InvalidRequestError("node-2", null)
        error.message.assert().isEqualTo("Invalid request [node-2]")
    }
}
