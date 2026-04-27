package me.ahoo.cobal.algorithm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.DefaultNode
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class WeightedRoundRobinLoadBalancerTest {
    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `weighted round robin should respect node weights`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-2", weight = 1)
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(state1, state2))
        val counts = mutableMapOf("node-1" to 0, "node-2" to 0)
        repeat(12) {
            val chosen = lb.choose()
            counts[chosen.node.id] = counts[chosen.node.id]!! + 1
        }
        counts["node-1"].assert().isEqualTo(9)
        counts["node-2"].assert().isEqualTo(3)
    }

    @Test
    fun `choose should skip unavailable node`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-2", weight = 1)
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(state1, state2))

        state2.circuitBreaker.onError(0, state2.circuitBreaker.timestampUnit, RuntimeException("error"))

        repeat(12) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when no nodes available`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(state1))

        state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))

        assertThrownBy<AllNodesUnavailableError> {
            lb.choose()
        }
    }

    @Test
    fun `concurrent choose should be thread-safe`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-2", weight = 1)
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(state1, state2))

        val threadCount = 10
        val iterations = 100
        val latch = CountDownLatch(threadCount)
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        counts["node-1"] = AtomicInteger(0)
        counts["node-2"] = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        repeat(threadCount) {
            thread {
                try {
                    repeat(iterations) {
                        val chosen = lb.choose()
                        counts[chosen.node.id]!!.incrementAndGet()
                    }
                } catch (_: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()

        errorCount.get().assert().isEqualTo(0)
        val total = counts["node-1"]!!.get() + counts["node-2"]!!.get()
        total.assert().isEqualTo(threadCount * iterations)
    }
}
