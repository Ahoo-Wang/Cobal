package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MutableClock(private var instant: Instant) : Clock() {
    override fun instant(): Instant = instant
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId): Clock = this
    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }
}

class SimpleNodeForState(override val id: NodeId, override val weight: Int = 1) : Node

class NodeStateTest {

    @Test
    fun `DefaultNodeState onFailure marks unavailable for retriable error`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node)

        val error = RateLimitError(node.id, null)
        state.onFailure(error)
        state.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        state.available.assert().isFalse()
    }

    @Test
    fun `DefaultNodeState onFailure does nothing for non-retriable error`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node)

        val error = AuthenticationError(node.id, null)
        state.onFailure(error)
        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        state.available.assert().isTrue()
    }

    @Test
    fun `onSuccess should reset failure count`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node, circuitOpenThreshold = 3)

        state.onFailure(RateLimitError(node.id, null))
        state.onFailure(RateLimitError(node.id, null))
        state.onSuccess()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
    }

    @Test
    fun `circuit breaker should open after threshold failures`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node, circuitOpenThreshold = 3)

        repeat(3) {
            state.onFailure(RateLimitError(node.id, null))
        }

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `circuit breaker should transition to HALF_OPEN after recovery time`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(clock.instant().plusSeconds(30), error)
                else -> null
            }
        }
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node, failurePolicy = testPolicy, circuitOpenThreshold = 2, clock = clock)

        state.onFailure(RateLimitError(node.id, null))
        state.onFailure(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        clock.advance(Duration.ofSeconds(60))

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)
        state.available.assert().isTrue()
    }

    @Test
    fun `onSuccess in HALF_OPEN should transition to AVAILABLE`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(clock.instant().plusSeconds(30), error)
                else -> null
            }
        }
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node, failurePolicy = testPolicy, circuitOpenThreshold = 2, clock = clock)

        state.onFailure(RateLimitError(node.id, null))
        state.onFailure(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        clock.advance(Duration.ofSeconds(60))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.onSuccess()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        state.available.assert().isTrue()
    }

    @Test
    fun `onFailure in HALF_OPEN should re-open circuit`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node, circuitOpenThreshold = 2)

        state.onFailure(RateLimitError(node.id, null))
        state.onFailure(RateLimitError(node.id, null))

        state.onFailure(ServerError(node.id, null))

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `concurrent onFailure calls should be thread-safe`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node, circuitOpenThreshold = 100)
        val threadCount = 50
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        repeat(threadCount) {
            thread {
                try {
                    state.onFailure(RateLimitError(node.id, null))
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()

        errorCount.get().assert().isEqualTo(0)
        state.status.assert().isNotNull()
    }
}
