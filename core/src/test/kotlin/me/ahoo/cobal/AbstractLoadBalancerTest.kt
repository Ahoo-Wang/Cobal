package me.ahoo.cobal

import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.RateLimitError
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.cobal.state.NodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AbstractLoadBalancerTest {

    private class FixedLoadBalancer<NODE : Node>(
        id: LoadBalancerId,
        states: List<NodeState<NODE>>,
        private val fixed: NodeState<NODE>
    ) : AbstractLoadBalancer<NODE>(id, states) {
        override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> = fixed
    }

    @Test
    fun `choose should delegate to doChoose when nodes available`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)
        val lb = FixedLoadBalancer("test-lb", listOf(state), state)
        lb.choose().assert().isEqualTo(state)
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when no nodes available`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)
        val error = RateLimitError(node.id, RuntimeException("429"))
        state.fail(error)

        val lb = FixedLoadBalancer("test-lb", listOf(state), state)
        val ex = assertThrows<AllNodesUnavailableError> { lb.choose() }
        ex.loadBalancerId.assert().isEqualTo("test-lb")
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when states empty`() {
        val lb = object : AbstractLoadBalancer<DefaultNode>("empty-lb", emptyList()) {
            override fun doChoose(available: List<NodeState<DefaultNode>>): NodeState<DefaultNode> {
                throw AssertionError("doChoose should not be called when no nodes available")
            }
        }
        val ex = assertThrows<AllNodesUnavailableError> { lb.choose() }
        ex.loadBalancerId.assert().isEqualTo("empty-lb")
    }
}
