package me.ahoo.cobal.algorithm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.DefaultNode
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.cobal.state.NodeState
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AbstractLoadBalancerTest {

    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    private class FixedLoadBalancer(
        id: LoadBalancerId,
        states: List<NodeState<DefaultNode>>,
    ) : AbstractLoadBalancer<DefaultNode>(id, states) {
        override fun doChoose(available: List<NodeState<DefaultNode>>): NodeState<DefaultNode> = available.first()
    }

    private class TrackingLoadBalancer(
        id: LoadBalancerId,
        states: List<NodeState<DefaultNode>>,
        val stateChangedLatch: CountDownLatch = CountDownLatch(1),
    ) : AbstractLoadBalancer<DefaultNode>(id, states) {
        var stateChangedCount = 0
            private set

        override fun doChoose(available: List<NodeState<DefaultNode>>): NodeState<DefaultNode> = available.first()

        override fun onStateChanged() {
            stateChangedCount++
            stateChangedLatch.countDown()
        }
    }

    @Test
    fun `choose should delegate to doChoose when nodes available`() {
        val node1 = DefaultNode("node-1")
        val state1 = DefaultNodeState(node1)
        val lb = FixedLoadBalancer("test-lb", listOf(state1))

        val chosen = lb.choose()
        chosen.node.id.assert().isEqualTo("node-1")
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when all nodes unavailable`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))
        val lb = FixedLoadBalancer("test-lb", listOf(state1, state2))

        state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))
        state2.circuitBreaker.onError(0, state2.circuitBreaker.timestampUnit, RuntimeException("error"))

        assertThrownBy<AllNodesUnavailableError> {
            lb.choose()
        }
    }

    @Test
    fun `constructor should throw IllegalStateException when states empty`() {
        assertThrownBy<IllegalStateException> {
            FixedLoadBalancer("test-lb", emptyList())
        }
    }

    @Test
    fun `availableStates should update when node becomes unavailable`() {
        val node1 = DefaultNode("node-1")
        val node2 = DefaultNode("node-2")
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val state2 = DefaultNodeState(node2, CircuitBreaker.of("node-2", strictCircuitBreakerConfig()))
        val lb = FixedLoadBalancer("test-lb", listOf(state1, state2))

        lb.availableStates.size.assert().isEqualTo(2)

        state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))

        lb.availableStates.size.assert().isEqualTo(1)
        lb.availableStates.first().node.id.assert().isEqualTo("node-2")
    }

    @Test
    fun `onStateChanged should be called when state transitions`() {
        val node1 = DefaultNode("node-1")
        val state1 = DefaultNodeState(node1, CircuitBreaker.of("node-1", strictCircuitBreakerConfig()))
        val lb = TrackingLoadBalancer("test-lb", listOf(state1))

        lb.stateChangedCount.assert().isEqualTo(0)

        state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))

        lb.stateChangedLatch.await(5, TimeUnit.SECONDS)
        lb.stateChangedCount.assert().isGreaterThanOrEqualTo(1)
    }
}
