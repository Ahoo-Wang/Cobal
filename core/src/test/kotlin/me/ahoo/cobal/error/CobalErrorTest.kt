package me.ahoo.cobal.error

import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class CobalErrorTest {

    @Test
    fun `CobalError should extend RuntimeException`() {
        val cause = RuntimeException("root")
        val error = object : CobalError("test message", cause) {}
        error.assert().isInstanceOf(RuntimeException::class.java)
        error.message.assert().isEqualTo("test message")
        error.cause.assert().isEqualTo(cause)
    }

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
    fun `AllNodesUnavailableError contains loadBalancerId`() {
        val error = AllNodesUnavailableError("lb-1")
        error.loadBalancerId.assert().isEqualTo("lb-1")
        error.message.assert().isEqualTo("All nodes unavailable in load balancer: lb-1")
    }
}
