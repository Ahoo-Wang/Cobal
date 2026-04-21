package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.SimpleNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class RandomLoadBalancerTest {
    @Test
    fun `choose should return available node`() {
        val node1 = SimpleNode("node-1")
        val node2 = SimpleNode("node-2")
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = RandomLoadBalancer("random-lb", listOf(state1, state2))
        val chosen = lb.choose()
        listOf("node-1", "node-2").assert().contains(chosen.node.id)
    }

    @Test
    fun `choose should skip unavailable node`() {
        val node1 = SimpleNode("node-1")
        val node2 = SimpleNode("node-2")
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = RandomLoadBalancer("random-lb", listOf(state1, state2))
        val error = RateLimitError(node1.id, RuntimeException("429"))

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
        val state1 = DefaultNodeState(node1)
        val lb = RandomLoadBalancer("random-lb", listOf(state1))
        val error = RateLimitError(node1.id, RuntimeException("429"))
        val selected = lb.choose()
        selected.onFailure(error)
        org.junit.jupiter.api.assertThrows<AllNodesUnavailableError> {
            lb.choose()
        }
    }
}