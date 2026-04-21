package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class SimpleNodeForState(override val id: NodeId, override val weight: Int = 1) : Node

class NodeStateTest {

    @Test
    fun `DefaultNodeState onFailure marks unavailable for retriable error`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node)

        val error = RateLimitError(node.id, null)
        state.onFailure(error)
        state.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        state.available.assert().isFalse()
    }

    @Test
    fun `DefaultNodeState onFailure does nothing for non-retriable error`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node)

        val error = AuthenticationError(node.id, null)
        state.onFailure(error)
        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        state.available.assert().isTrue()
    }
}
