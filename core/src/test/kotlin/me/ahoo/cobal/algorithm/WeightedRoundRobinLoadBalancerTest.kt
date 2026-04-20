package me.ahoo.cobal.algorithm

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class WeightedRoundRobinLoadBalancerTest {
    @Test
    fun `weighted round robin should respect node weights`() {
        val node1 = SimpleNode("node-1", weight = 3)
        val node2 = SimpleNode("node-2", weight = 1)
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(node1, node2))
        // node-1 should be chosen 3 times, node-2 once in a cycle of 4
        val counts = mutableMapOf("node-1" to 0, "node-2" to 0)
        repeat(12) { // 3 cycles
            val chosen = lb.choose()
            counts[chosen.node.id] = counts[chosen.node.id]!! + 1
        }
        counts["node-1"].assert().isEqualTo(9) // 3 * 3
        counts["node-2"].assert().isEqualTo(3)  // 3 * 1
    }
}