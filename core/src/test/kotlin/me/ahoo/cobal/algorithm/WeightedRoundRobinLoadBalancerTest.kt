package me.ahoo.cobal.algorithm

import me.ahoo.cobal.DefaultNode
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class WeightedRoundRobinLoadBalancerTest {
    @Test
    fun `weighted round robin should respect node weights`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-2", weight = 1)
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(state1, state2))
        // node-1 should be chosen 3 times, node-2 once in a cycle of 4
        val counts = mutableMapOf("node-1" to 0, "node-2" to 0)
        repeat(12) { // 3 cycles
            val chosen = lb.choose()
            counts[chosen.node.id] = counts[chosen.node.id]!! + 1
        }
        counts["node-1"].assert().isEqualTo(9) // 3 * 3
        counts["node-2"].assert().isEqualTo(3) // 3 * 1
    }
}
