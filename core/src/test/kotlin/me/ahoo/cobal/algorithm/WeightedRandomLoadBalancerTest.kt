package me.ahoo.cobal.algorithm

import me.ahoo.cobal.DefaultNode
import me.ahoo.cobal.state.DefaultNodeState
import kotlin.test.Test
import kotlin.test.assertEquals

class WeightedRandomLoadBalancerTest {
    @Test
    fun `choose should return single available node`() {
        val node = DefaultNode("node-1", weight = 5)
        val state = DefaultNodeState(node)
        val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state))
        repeat(10) {
            assertEquals("node-1", lb.choose().node.id)
        }
    }
}
