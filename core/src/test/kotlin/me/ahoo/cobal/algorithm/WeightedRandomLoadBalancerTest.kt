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
import kotlin.test.assertTrue

class WeightedRandomLoadBalancerTest {
    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `choose should return single available node`() {
        val node = DefaultNode("node-1", weight = 5)
        val state = DefaultNodeState(node)
        val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state))
        repeat(10) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }

    @Test
    fun `choose should distribute according to weights`() {
        val node1 = DefaultNode("node-1", weight = 1)
        val node2 = DefaultNode("node-2", weight = 2)
        val node3 = DefaultNode("node-3", weight = 3)
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val state3 = DefaultNodeState(node3)
        val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state1, state2, state3))

        val counts = mutableMapOf("node-1" to 0, "node-2" to 0, "node-3" to 0)
        val iterations = 60_000
        repeat(iterations) {
            val chosen = lb.choose().node.id
            counts[chosen] = counts[chosen]!! + 1
        }

        // Expected: node-1 ~ 10000 (1/6), node-2 ~ 20000 (2/6), node-3 ~ 30000 (3/6)
        // Tolerance: +-10%
        assertTrue(counts["node-1"]!! in 9000..11000, "node-1 count ${counts["node-1"]} not in expected range")
        assertTrue(counts["node-2"]!! in 18000..22000, "node-2 count ${counts["node-2"]} not in expected range")
        assertTrue(counts["node-3"]!! in 27000..33000, "node-3 count ${counts["node-3"]} not in expected range")
    }

    @Test
    fun `choose should rebuild alias table on state change`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-2", weight = 1)
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))
        val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state1, state2))

        state2.circuitBreaker.onError(0, state2.circuitBreaker.timestampUnit, RuntimeException("error"))

        repeat(10) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when no nodes available`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state1))

        state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))

        assertThrownBy<AllNodesUnavailableError> {
            lb.choose()
        }
    }

    @Test
    fun `choose should handle equal weight nodes`() {
        val node1 = DefaultNode("node-1", weight = 2)
        val node2 = DefaultNode("node-2", weight = 2)
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state1, state2))

        val counts = mutableMapOf("node-1" to 0, "node-2" to 0)
        repeat(10_000) {
            val chosen = lb.choose().node.id
            counts[chosen] = counts[chosen]!! + 1
        }

        // With equal weights, both should be ~5000 +-10%
        assertTrue(counts["node-1"]!! in 4500..5500, "node-1 count ${counts["node-1"]} not in expected range")
        assertTrue(counts["node-2"]!! in 4500..5500, "node-2 count ${counts["node-2"]} not in expected range")
    }
}
