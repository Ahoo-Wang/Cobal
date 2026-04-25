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

class RandomLoadBalancerTest {

    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `choose should return available node`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = RandomLoadBalancer("random-lb", listOf(state1, state2))

        val chosen = lb.choose()
        listOf("node-1", "node-2").assert().contains(chosen.node.id)
    }

    @Test
    fun `choose should skip unavailable node`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))
        val lb = RandomLoadBalancer("random-lb", listOf(state1, state2))

        state2.circuitBreaker.onError(0, state2.circuitBreaker.timestampUnit, RuntimeException("error"))

        repeat(20) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }

    @Test
    fun `choose should return single available node`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("random-lb", listOf(state))

        repeat(10) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }

    @Test
    fun `all nodes unavailable should throw`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))
        val lb = RandomLoadBalancer("random-lb", listOf(state1, state2))

        state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))
        state2.circuitBreaker.onError(0, state2.circuitBreaker.timestampUnit, RuntimeException("error"))

        assertThrownBy<AllNodesUnavailableError> {
            lb.choose()
        }
    }
}
