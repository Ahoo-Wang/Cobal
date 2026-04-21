package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.NodeId
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class SimpleNode(override val id: NodeId, override val weight: Int = 1) : Node

class RandomLoadBalancerTest {
    @Test
    fun `choose should return available node`() {
        val node1 = SimpleNode("node-1")
        val node2 = SimpleNode("node-2")
        val lb = RandomLoadBalancer("random-lb", listOf(node1, node2))
        val chosen = lb.choose()
        listOf("node-1", "node-2").assert().contains(chosen.node.id)
    }

    @Test
    fun `choose should skip unavailable node`() {
        val node1 = SimpleNode("node-1")
        val node2 = SimpleNode("node-2")
        val lb = RandomLoadBalancer("random-lb", listOf(node1, node2))
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))

        // Mark both nodes as unavailable
        val selected1 = lb.choose()
        selected1.onFailure(error)

        val selected2 = lb.choose()
        selected2.onFailure(error)

        // Now no nodes should be available
        org.junit.jupiter.api.assertThrows<AllNodesUnavailableError> {
            lb.choose()
        }
    }

    @Test
    fun `all nodes unavailable should throw`() {
        val node1 = SimpleNode("node-1")
        val lb = RandomLoadBalancer("random-lb", listOf(node1))
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        val selected = lb.choose()
        selected.onFailure(error)
        org.junit.jupiter.api.assertThrows<AllNodesUnavailableError> {
            lb.choose()
        }
    }
}
