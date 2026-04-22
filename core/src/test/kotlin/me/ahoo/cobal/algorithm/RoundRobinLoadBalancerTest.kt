package me.ahoo.cobal.algorithm

import me.ahoo.cobal.DefaultNode
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class RoundRobinLoadBalancerTest {
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
        val state2 = DefaultNodeState(node2)
        val lb = RoundRobinLoadBalancer("rr-lb", listOf(state1, state2))
        val error = RateLimitError(node2.id, RuntimeException("429"))
        lb.choose() // node-1
        val selected2 = lb.choose() // node-2
        selected2.fail(error) // mark node-2 unavailable
        lb.choose().node.id.assert().isEqualTo("node-1") // back to node-1
    }
}
