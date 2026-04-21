package me.ahoo.cobal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SimpleNodeForState(override val id: NodeId, override val weight: Int = 1) : Node

class NodeStateTest {

    @Test
    fun `DefaultNodeState onFailure marks unavailable for retriable error`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node)

        val error = RateLimitError(node.id, null)
        state.onFailure(error)
        assertEquals(NodeStatus.UNAVAILABLE, state.status)
        assertFalse(state.available)
    }

    @Test
    fun `DefaultNodeState onFailure does nothing for non-retriable error`() {
        val node = SimpleNodeForState("node-1")
        val state = DefaultNodeState(node)

        val error = AuthenticationError(node.id, null)
        state.onFailure(error)
        assertEquals(NodeStatus.AVAILABLE, state.status)
        assertEquals(true, state.available)
    }
}
