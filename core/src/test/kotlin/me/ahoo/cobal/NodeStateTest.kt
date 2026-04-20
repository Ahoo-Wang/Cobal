package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SimpleNodeForState(override val id: NodeId) : Node

class NodeStateTest {

    @Test
    fun `available node should have AVAILABLE status`() {
        val node = SimpleNodeForState("node-1")
        val nodeState = DefaultNodeState(node)
        nodeState.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        nodeState.available.assert().isTrue()
    }

    @Test
    fun `onFailure with RATE_LIMITED should mark node UNAVAILABLE`() {
        val node = SimpleNodeForState("node-1")
        val nodeState = DefaultNodeState(node, circuitOpenThreshold = 3)
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        nodeState.available.assert().isFalse()
    }

    @Test
    fun `onFailure with INVALID_REQUEST should not change status`() {
        val node = SimpleNodeForState("node-1")
        val nodeState = DefaultNodeState(node)
        val error = NodeError(ErrorCategory.INVALID_REQUEST, RuntimeException("400"))
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        nodeState.available.assert().isTrue()
    }

    @Test
    fun `consecutive failures should trigger CIRCUIT_OPEN`() {
        val node = SimpleNodeForState("node-1")
        val nodeState = DefaultNodeState(node, circuitOpenThreshold = 2)
        val error = NodeError(ErrorCategory.SERVER_ERROR, RuntimeException("500"))
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
    }

    @Test
    fun `node should auto-recover when recoverAt is passed`() {
        val node = SimpleNodeForState("node-1")
        val pastPolicy = NodeFailurePolicy {
            NodeFailureDecision(Instant.now().minus(1, ChronoUnit.SECONDS))
        }
        val nodeState = DefaultNodeState(node, failurePolicy = pastPolicy)
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        nodeState.onFailure(error)
        // Since recoverAt is in the past, status should immediately be AVAILABLE
        val status = nodeState.status
        status.assert().isEqualTo(NodeStatus.AVAILABLE)
        nodeState.available.assert().isTrue()
    }
}