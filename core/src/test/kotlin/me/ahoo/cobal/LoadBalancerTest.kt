package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class SimpleNode(override val id: NodeId, override val weight: Int = 1) : Node

class LoadBalancerTest {
    @Test
    fun `LoadBalancer choose should return NodeState`() {
        val node = SimpleNode("node-1")
        val loadBalancer = object : LoadBalancer<SimpleNode> {
            override val id: LoadBalancerId = "test-lb"
            override val nodes: List<SimpleNode> = listOf(node)
            override fun choose(): NodeState<SimpleNode> = DefaultNodeState(node)
        }
        val selected = loadBalancer.choose()
        selected.node.id.assert().isEqualTo("node-1")
        selected.available.assert().isTrue()
    }
}
