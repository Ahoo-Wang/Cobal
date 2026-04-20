package me.ahoo.cobal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Clock
import java.time.Instant

enum class NodeStatus {
    AVAILABLE,
    UNAVAILABLE,
    CIRCUIT_OPEN
}

sealed interface NodeEvent {
    val nodeId: NodeId
    data class MarkedUnavailable(override val nodeId: NodeId, val recoverAt: Instant) : NodeEvent
    data class Recovered(override val nodeId: NodeId) : NodeEvent
}

enum class ErrorCategory {
    RATE_LIMITED,
    SERVER_ERROR,
    AUTHENTICATION,
    INVALID_REQUEST,
    TIMEOUT,
    NETWORK
}

open class CobalError(
    message: String?,
    override val cause: Throwable?
) : Exception(message, cause)

class NodeError(
    val category: ErrorCategory,
    override val cause: Throwable
) : CobalError(cause?.message, cause)

data class NodeFailureDecision(val recoverAt: Instant)

fun interface NodeFailurePolicy {
    fun evaluate(error: NodeError): NodeFailureDecision?

    companion object {
        val Default = NodeFailurePolicy { error ->
            when (error.category) {
                ErrorCategory.INVALID_REQUEST -> null
                ErrorCategory.RATE_LIMITED -> NodeFailureDecision(Instant.now() + java.time.Duration.ofSeconds(30))
                ErrorCategory.SERVER_ERROR -> NodeFailureDecision(Instant.MAX)
                else -> NodeFailureDecision(Instant.MAX)
            }
        }
    }
}

interface NodeState<NODE : Node> {
    val node: NODE
    val watch: Flow<NodeEvent>
    val status: NodeStatus
    val available: Boolean
        get() = status == NodeStatus.AVAILABLE

    fun onFailure(error: NodeError)
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
        get() {
            val currentRecoverAt = recoverAt
            return when {
                circuitOpened -> NodeStatus.CIRCUIT_OPEN
                failureCount >= circuitOpenThreshold -> {
                    circuitOpened = true
                    NodeStatus.CIRCUIT_OPEN
                }
                currentRecoverAt == Instant.MAX -> NodeStatus.AVAILABLE
                currentRecoverAt != null && currentRecoverAt.isAfter(clock.instant()) -> NodeStatus.UNAVAILABLE
                else -> {
                    if (currentRecoverAt != null && currentRecoverAt != Instant.MAX) {
                        failureCount = 0
                        recoverAt = null
                        events.tryEmit(NodeEvent.Recovered(node.id))
                    }
                    NodeStatus.AVAILABLE
                }
            }
        }

    override fun onFailure(error: NodeError) {
        failureCount++
        failurePolicy.evaluate(error)?.let { decision ->
            this.recoverAt = decision.recoverAt
            events.tryEmit(NodeEvent.MarkedUnavailable(node.id, decision.recoverAt))
        }
    }
}
