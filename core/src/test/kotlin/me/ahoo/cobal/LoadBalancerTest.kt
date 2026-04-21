package me.ahoo.cobal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LoadBalancerTest {

    @Test
    fun `LoadBalancer has states property and availableStates computed property`() {
        val node = object : Node {
            override val id: NodeId = "node-1"
            override val weight: Int = 1
        }
        val state = DefaultNodeState(node)
        val lb = object : LoadBalancer<Node> {
            override val id: LoadBalancerId = "lb-1"
            override val states: List<NodeState<Node>> = listOf(state)
            override fun choose(): NodeState<Node> = states.first()
        }
        assertEquals(listOf(state), lb.states)
        assertEquals(listOf(state), lb.availableStates)
    }
}