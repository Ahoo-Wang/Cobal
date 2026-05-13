package me.ahoo.cobal.error

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class AllNodesUnavailableErrorTest {
    @Test
    fun `AllNodesUnavailableError contains loadBalancerId`() {
        val error = AllNodesUnavailableError("lb-1")
        error.loadBalancerId.assert().isEqualTo("lb-1")
        error.message.assert().isEqualTo("All nodes unavailable in load balancer: lb-1")
        error.cause.assert().isNull()
        error.suppressedExceptions.size.assert().isEqualTo(0)
    }

    @Test
    fun `AllNodesUnavailableError uses latest failure as cause and others as suppressed`() {
        val first = NetworkError("node-1", null)
        val second = RateLimitError("node-2", null)
        val third = ServerError("node-3", null)
        val error = AllNodesUnavailableError("lb-1", listOf(first, second, third))

        error.cause.assert().isEqualTo(third)
        error.suppressedExceptions.assert().containsExactly(first, second)
    }

    @Test
    fun `AllNodesUnavailableError with single failure has it as cause and no suppressed`() {
        val only = NetworkError("node-1", null)
        val error = AllNodesUnavailableError("lb-1", listOf(only))

        error.cause.assert().isEqualTo(only)
        error.suppressedExceptions.size.assert().isEqualTo(0)
    }
}
