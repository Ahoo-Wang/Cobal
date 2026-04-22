package me.ahoo.cobal

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import me.ahoo.cobal.state.CircuitBreakerStatus
import me.ahoo.cobal.state.DefaultCircuitBreaker
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.cobal.state.NodeStatus
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class NodeStateTest {

    @Test
    fun `DefaultNodeState onError marks unavailable for retriable error`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)

        val error = RateLimitError(node.id, null)
        state.onError(error)
        state.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        state.available.assert().isFalse()
    }

    @Test
    fun `onSuccess should reset failure count`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 3))

        state.onError(RateLimitError(node.id, null))
        state.onError(RateLimitError(node.id, null))
        state.onSuccess()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
    }

    @Test
    fun `circuit breaker should open after threshold failures`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 3))

        repeat(3) {
            state.onError(RateLimitError(node.id, null))
        }

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `circuit breaker should transition to HALF_OPEN after recovery time`() = runTest {
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(
            node,
            scope = this,
            failurePolicy = testPolicy,
            circuitBreaker = DefaultCircuitBreaker(threshold = 2, recoveryDuration = Duration.ofSeconds(30)),
        )

        state.onError(RateLimitError(node.id, null))
        state.onError(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        advanceTimeBy(60_000)

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)
        state.available.assert().isTrue()
    }

    @Test
    fun `onSuccess in HALF_OPEN should transition to AVAILABLE`() = runTest {
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(
            node,
            scope = this,
            failurePolicy = testPolicy,
            circuitBreaker = DefaultCircuitBreaker(threshold = 2, recoveryDuration = Duration.ofSeconds(30)),
        )

        state.onError(RateLimitError(node.id, null))
        state.onError(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        advanceTimeBy(60_000)
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.onSuccess()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        state.available.assert().isTrue()
    }

    @Test
    fun `onError in HALF_OPEN should re-open circuit`() = runTest {
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(
            node,
            scope = this,
            failurePolicy = testPolicy,
            circuitBreaker = DefaultCircuitBreaker(threshold = 2, recoveryDuration = Duration.ofSeconds(30)),
        )

        state.onError(RateLimitError(node.id, null))
        state.onError(RateLimitError(node.id, null))

        advanceTimeBy(60_000)
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.onError(ServerError(node.id, null))

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `concurrent onError calls should be thread-safe`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 100))
        val threadCount = 50
        val threads = mutableListOf<Thread>()
        val errorCount = java.util.concurrent.atomic.AtomicInteger(0)

        repeat(threadCount) {
            val t = Thread {
                try {
                    state.onError(RateLimitError(node.id, null))
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
        val cb = DefaultCircuitBreaker(threshold = 3)
        val state = DefaultNodeState(node, circuitBreaker = cb)

        state.circuitBreaker.assert().isSameAs(cb)
        state.circuitBreaker.status.assert().isEqualTo(CircuitBreakerStatus.CLOSED)
    }

    @Test
    fun `non-policy errors should open circuit without UNAVAILABLE`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 2))

        state.onError(ServerError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)

        state.onError(ServerError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
    }
}
