package me.ahoo.cobal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CobalErrorTest {

    @Test
    fun `CobalError is abstract and extends RuntimeException`() {
        val cause = RuntimeException("original")
        val error = object : CobalError("test", cause) {}
        assertIs<CobalError>(error)
        assertIs<RuntimeException>(error)
        assertEquals("test", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `RetriableError is a marker interface`() {
        val error = RateLimitError("node-1", null)
        assertIs<RetriableError>(error)
        assertIs<NodeError>(error)
        assertEquals("node-1", error.nodeId)
        assertTrue(error.message!!.contains("node-1"))
    }

    @Test
    fun `RateLimitError is retriable`() {
        val error = RateLimitError("node-1", null)
        assertIs<RetriableError>(error)
        assertEquals("Rate limited [node-1]", error.message)
    }

    @Test
    fun `ServerError is retriable`() {
        val error = ServerError("node-2", null)
        assertIs<RetriableError>(error)
        assertEquals("Server error [node-2]", error.message)
    }

    @Test
    fun `TimeoutError is retriable`() {
        val error = TimeoutError("node-3", null)
        assertIs<RetriableError>(error)
        assertEquals("Timeout [node-3]", error.message)
    }

    @Test
    fun `NetworkError is retriable`() {
        val error = NetworkError("node-4", null)
        assertIs<RetriableError>(error)
        assertEquals("Network error [node-4]", error.message)
    }

    @Test
    fun `AuthenticationError is not retriable`() {
        val error = AuthenticationError("node-5", null)
        assertIs<NodeError>(error)
        assertTrue(error !is RetriableError)
        assertEquals("Auth failed [node-5]", error.message)
    }

    @Test
    fun `InvalidRequestError is not retriable`() {
        val error = InvalidRequestError("node-6", null)
        assertTrue(error !is RetriableError)
        assertEquals("Invalid request [node-6]", error.message)
    }

    @Test
    fun `AllNodesUnavailableError contains loadBalancerId`() {
        val error = AllNodesUnavailableError("lb-1")
        assertEquals("All nodes unavailable in load balancer: lb-1", error.message)
        assertEquals("lb-1", error.loadBalancerId)
    }

    @Test
    fun `NodeFailurePolicy returns NodeFailureDecision for retriable errors`() {
        val policy = NodeFailurePolicy.Default
        val retriable = RateLimitError("node-1", null)
        assertIs<NodeFailureDecision>(policy.evaluate(retriable))
    }

    @Test
    fun `NodeFailurePolicy returns null for non-retriable errors`() {
        val policy = NodeFailurePolicy.Default
        val nonRetriable = AuthenticationError("node-1", null)
        assertEquals(null, policy.evaluate(nonRetriable))
    }
}
