package me.ahoo.cobal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SimpleNode(override val id: NodeId, override val weight: Int = 1) : Node

class LoadBalancerTest {

    @Test
    fun `LoadBalancer has states property and availableStates computed property`() {
        val node = SimpleNode("node-1")
        val state = DefaultNodeState(node)
        val lb = object : LoadBalancer<SimpleNode> {
            override val id: LoadBalancerId = "lb-1"
            override val states: List<NodeState<SimpleNode>> = listOf(state)
            override fun choose(): NodeState<SimpleNode> = states.first()
        }
        assertEquals(listOf(state), lb.states)
        assertEquals(listOf(state), lb.availableStates)
    }
}
