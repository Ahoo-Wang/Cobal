package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancerTest {

    @Test
    fun `LoadBalancer has states property and availableStates computed property`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)
        val lb = object : LoadBalancer<DefaultNode> {
            override val id: LoadBalancerId = "lb-1"
            override val states: List<NodeState<DefaultNode>> = listOf(state)
            override fun choose(): NodeState<DefaultNode> = states.first()
        }
        lb.states.assert().isEqualTo(listOf(state))
        lb.availableStates.assert().isEqualTo(listOf(state))
    }
}
