package me.ahoo.cobal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Clock
import java.time.Instant

enum class NodeStatus {
    AVAILABLE,
    UNAVAILABLE,
    CIRCUIT_OPEN,
    CIRCUIT_HALF_OPEN
}

sealed interface NodeEvent {
    val nodeId: NodeId
    data class MarkedUnavailable(override val nodeId: NodeId, val recoverAt: Instant) : NodeEvent
    data class Recovered(override val nodeId: NodeId) : NodeEvent
    data class CircuitOpened(override val nodeId: NodeId) : NodeEvent
    data class CircuitHalfOpen(override val nodeId: NodeId) : NodeEvent
}

interface NodeState<NODE : Node> {
    val node: NODE
    val watch: Flow<NodeEvent>
    val status: NodeStatus
    val available: Boolean
        get() = status == NodeStatus.AVAILABLE || status == NodeStatus.CIRCUIT_HALF_OPEN

    fun onFailure(error: CobalError)
    fun onSuccess()
}

class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    private val failurePolicy: NodeFailurePolicy = NodeFailurePolicy.Default,
    private val circuitOpenThreshold: Int = 5,
    private val clock: Clock = Clock.systemUTC()
) : NodeState<NODE> {
    private var recoverAt: Instant? = null
    private var failureCount: Int = 0
    private var circuitOpened: Boolean = false
    private val events = MutableSharedFlow<NodeEvent>()

    override val watch: Flow<NodeEvent> = events.asSharedFlow()

    override val status: NodeStatus
        get() = synchronized(this) {
            when {
                circuitOpened -> {
                    val currentRecoverAt = recoverAt
                    if (currentRecoverAt != null && !currentRecoverAt.isAfter(clock.instant())) {
                        NodeStatus.CIRCUIT_HALF_OPEN
                    } else {
                        NodeStatus.CIRCUIT_OPEN
                    }
                }
                failureCount >= circuitOpenThreshold -> {
                    circuitOpened = true
                    NodeStatus.CIRCUIT_OPEN
                }
                else -> {
                    val currentRecoverAt = recoverAt
                    when {
                        currentRecoverAt == Instant.MAX -> NodeStatus.AVAILABLE
                        currentRecoverAt != null && currentRecoverAt.isAfter(clock.instant()) -> NodeStatus.UNAVAILABLE
                        else -> NodeStatus.AVAILABLE
                    }
                }
            }
        }

    override fun onFailure(error: CobalError) {
        synchronized(this) {
            val currentStatus = status
            if (currentStatus == NodeStatus.CIRCUIT_HALF_OPEN) {
                circuitOpened = true
                failurePolicy.evaluate(error)?.let { decision ->
                    recoverAt = decision.recoverAt
                }
                events.tryEmit(NodeEvent.MarkedUnavailable(node.id, recoverAt!!))
                return
            }
            failureCount++
            if (failureCount >= circuitOpenThreshold) {
                circuitOpened = true
                events.tryEmit(NodeEvent.CircuitOpened(node.id))
            }
            failurePolicy.evaluate(error)?.let { decision ->
                recoverAt = decision.recoverAt
                events.tryEmit(NodeEvent.MarkedUnavailable(node.id, recoverAt!!))
            }
        }
    }

    override fun onSuccess() {
        synchronized(this) {
            val currentStatus = status
            if (currentStatus == NodeStatus.CIRCUIT_HALF_OPEN || currentStatus == NodeStatus.UNAVAILABLE) {
                failureCount = 0
                circuitOpened = false
                recoverAt = null
                events.tryEmit(NodeEvent.Recovered(node.id))
            } else if (failureCount > 0) {
                failureCount = 0
            }
        }
    }
}
