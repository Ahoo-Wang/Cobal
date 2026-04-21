package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class CobalErrorTest {

    @Test
    fun `CobalError is abstract and extends RuntimeException`() {
        val cause = RuntimeException("original")
        val error = object : CobalError("test", cause) {}
        error.assert().isInstanceOf(CobalError::class.java)
        error.assert().isInstanceOf(RuntimeException::class.java)
        error.message.assert().isEqualTo("test")
        error.cause.assert().isEqualTo(cause)
    }

    @Test
    fun `RetriableError is a marker interface`() {
        val error = RateLimitError("node-1", null)
        error.assert().isInstanceOf(RetriableError::class.java)
        error.assert().isInstanceOf(NodeError::class.java)
        error.nodeId.assert().isEqualTo("node-1")
        error.message.assert().isNotNull().contains("node-1")
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
        error.assert().isInstanceOf(NodeError::class.java)
        error.assert().isNotInstanceOf(RetriableError::class.java)
        error.message.assert().isEqualTo("Auth failed [node-5]")
    }

    @Test
    fun `InvalidRequestError is not retriable`() {
        val error = InvalidRequestError("node-6", null)
        error.assert().isNotInstanceOf(RetriableError::class.java)
        error.message.assert().isEqualTo("Invalid request [node-6]")
    }

    @Test
    fun `AllNodesUnavailableError contains loadBalancerId`() {
        val error = AllNodesUnavailableError("lb-1")
        error.message.assert().isEqualTo("All nodes unavailable in load balancer: lb-1")
        error.loadBalancerId.assert().isEqualTo("lb-1")
    }

    @Test
    fun `NodeFailurePolicy returns NodeFailureDecision for retriable errors`() {
        val policy = NodeFailurePolicy.Default
        val retriable = RateLimitError("node-1", null)
        policy.evaluate(retriable).assert().isInstanceOf(NodeFailureDecision::class.java)
    }

    @Test
    fun `NodeFailurePolicy returns null for non-retriable errors`() {
        val policy = NodeFailurePolicy.Default
        val nonRetriable = AuthenticationError("node-1", null)
        policy.evaluate(nonRetriable).assert().isNull()
    }
}
