package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.DefaultNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration

class NodeStateTest {

    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `DefaultNodeState should be available by default`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)

        state.available.assert().isTrue()
        state.circuitBreaker.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `DefaultNodeState should delegate to circuitBreaker`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("node-1", strictCircuitBreakerConfig())
        val state = DefaultNodeState(node, cb)

        state.circuitBreaker.assert().isSameAs(cb)
    }

    @Test
    fun `DefaultNodeState should expose node property`() {
        val node = DefaultNode("node-1", weight = 3)
        val state = DefaultNodeState(node)

        state.node.id.assert().isEqualTo("node-1")
        state.node.weight.assert().isEqualTo(3)
    }

    @Test
    fun `available should be false when circuit is open`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))

        state.circuitBreaker.onError(0, state.circuitBreaker.timestampUnit, RuntimeException("error"))

        state.available.assert().isFalse()
        state.circuitBreaker.state.assert().isEqualTo(CircuitBreaker.State.OPEN)
    }

    @Test
    fun `available should be false when weight is zero`() {
        val node = DefaultNode("node-1", weight = 0)
        val state = DefaultNodeState(node)

        state.circuitBreaker.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
        state.available.assert().isFalse()
    }

    @Test
    fun `available should be true when in HALF_OPEN state`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("node-1", strictCircuitBreakerConfig())
        val state = DefaultNodeState(node, cb)

        // Open the circuit first
        cb.onError(0, cb.timestampUnit, RuntimeException("error"))
        cb.state.assert().isEqualTo(CircuitBreaker.State.OPEN)

        // Transition to half-open
        cb.transitionToHalfOpenState()
        cb.state.assert().isEqualTo(CircuitBreaker.State.HALF_OPEN)

        state.available.assert().isTrue()
    }

    @Test
    fun `available should be false when in OPEN state`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))

        state.circuitBreaker.onError(0, state.circuitBreaker.timestampUnit, RuntimeException("error"))

        state.circuitBreaker.state.assert().isEqualTo(CircuitBreaker.State.OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `CircuitBreaker State available extension should map states correctly`() {
        CircuitBreaker.State.CLOSED.available.assert().isTrue()
        CircuitBreaker.State.OPEN.available.assert().isFalse()
        CircuitBreaker.State.HALF_OPEN.available.assert().isTrue()
        CircuitBreaker.State.DISABLED.available.assert().isTrue()
        CircuitBreaker.State.METRICS_ONLY.available.assert().isTrue()
    }

    @Test
    fun `DefaultNodeState should use default circuit breaker when not provided`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)

        state.circuitBreaker.assert().isNotNull()
        state.circuitBreaker.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
    }
}
