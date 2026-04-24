package me.ahoo.cobal

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.cobal.state.NodeStatus
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration

class NodeStateTest {

    private fun testCircuitBreakerConfig(
        slidingWindowSize: Int = 5,
        minimumNumberOfCalls: Int = slidingWindowSize,
        waitDurationInOpenState: Duration = Duration.ofSeconds(60),
    ): CircuitBreakerConfig = CircuitBreakerConfig.custom()
        .failureRateThreshold(100.0f)
        .slidingWindowType(SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(slidingWindowSize)
        .minimumNumberOfCalls(minimumNumberOfCalls)
        .waitDurationInOpenState(waitDurationInOpenState)
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build()

    @Test
    fun `DefaultNodeState onError marks unavailable for retriable error`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)

        val error = RateLimitError(node.id, null)
        state.fail(error)
        state.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        state.available.assert().isFalse()
    }

    @Test
    fun `onSuccess should reset circuit breaker failure count`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("test", testCircuitBreakerConfig(slidingWindowSize = 3))
        val state = DefaultNodeState(node, circuitBreaker = cb)

        state.fail(RateLimitError(node.id, null))
        state.fail(RateLimitError(node.id, null))
        state.succeed()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
    }

    @Test
    fun `circuit breaker should open after threshold failures`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("test", testCircuitBreakerConfig(slidingWindowSize = 3))
        val state = DefaultNodeState(node, circuitBreaker = cb)

        repeat(3) {
            state.fail(RateLimitError(node.id, null))
        }

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `circuit breaker should transition to HALF_OPEN after recovery`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("test", testCircuitBreakerConfig(slidingWindowSize = 2))
        val state = DefaultNodeState(node, circuitBreaker = cb)

        state.fail(RateLimitError(node.id, null))
        state.fail(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        cb.transitionToHalfOpenState()

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)
        state.available.assert().isTrue()
    }

    @Test
    fun `onSuccess in HALF_OPEN should transition to AVAILABLE`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("test", testCircuitBreakerConfig(slidingWindowSize = 2))
        val state = DefaultNodeState(node, circuitBreaker = cb)

        state.fail(RateLimitError(node.id, null))
        state.fail(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        cb.transitionToHalfOpenState()
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.succeed()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        state.available.assert().isTrue()
    }

    @Test
    fun `onError in HALF_OPEN should re-open circuit`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("test", testCircuitBreakerConfig(slidingWindowSize = 2))
        val state = DefaultNodeState(node, circuitBreaker = cb)

        state.fail(RateLimitError(node.id, null))
        state.fail(RateLimitError(node.id, null))

        cb.transitionToHalfOpenState()
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.fail(ServerError(node.id, null))

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `concurrent onError calls should be thread-safe`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("test", testCircuitBreakerConfig(slidingWindowSize = 100))
        val state = DefaultNodeState(node, circuitBreaker = cb)
        val threadCount = 50
        val threads = mutableListOf<Thread>()
        val errorCount = java.util.concurrent.atomic.AtomicInteger(0)

        repeat(threadCount) {
            val t = Thread {
                try {
                    state.fail(RateLimitError(node.id, null))
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
            threads.add(t)
            t.start()
        }
        threads.forEach { it.join() }

        errorCount.get().assert().isEqualTo(0)
        state.status.assert().isNotNull()
    }

    @Test
    fun `NodeState should expose circuitBreaker property`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("test", testCircuitBreakerConfig(slidingWindowSize = 3))
        val state = DefaultNodeState(node, circuitBreaker = cb)

        state.circuitBreaker.assert().isSameAs(cb)
        state.circuitBreaker.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `non-policy errors should open circuit without UNAVAILABLE`() {
        val node = DefaultNode("node-1")
        val cb = CircuitBreaker.of("test", testCircuitBreakerConfig(slidingWindowSize = 2))
        val state = DefaultNodeState(node, circuitBreaker = cb)

        state.fail(ServerError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)

        state.fail(ServerError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
    }
}
