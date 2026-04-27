package me.ahoo.cobal

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration

class LoadBalancerTest {

    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `LoadBalancer availableStates should filter by availability`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))

        val lb = object : LoadBalancer<DefaultNode> {
            override val id: LoadBalancerId = "test-lb"
            override val states = listOf(state1, state2)
            override fun choose(): me.ahoo.cobal.state.NodeState<DefaultNode> =
                availableStates.first()
        }

        lb.availableStates.size.assert().isEqualTo(2)

        state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))

        lb.availableStates.size.assert().isEqualTo(1)
        lb.availableStates.first().node.id.assert().isEqualTo("node-2")
    }
}
