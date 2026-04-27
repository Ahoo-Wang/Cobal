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

class RoundRobinLoadBalancerTest {

    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `choose should rotate through nodes in order`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = RoundRobinLoadBalancer("rr-lb", listOf(state1, state2))

        lb.choose().node.id.assert().isEqualTo("node-1")
        lb.choose().node.id.assert().isEqualTo("node-2")
        lb.choose().node.id.assert().isEqualTo("node-1")
    }

    @Test
    fun `choose should skip unavailable node`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))
        val lb = RoundRobinLoadBalancer("rr-lb", listOf(state1, state2))

        state2.circuitBreaker.onError(0, state2.circuitBreaker.timestampUnit, RuntimeException("error"))

        repeat(10) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }

    @Test
    fun `choose should handle single node`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)
        val lb = RoundRobinLoadBalancer("rr-lb", listOf(state))

        repeat(5) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when all nodes unavailable`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))
        val lb = RoundRobinLoadBalancer("rr-lb", listOf(state1, state2))

        state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))
        state2.circuitBreaker.onError(0, state2.circuitBreaker.timestampUnit, RuntimeException("error"))

        assertThrownBy<AllNodesUnavailableError> {
            lb.choose()
        }
    }
}
