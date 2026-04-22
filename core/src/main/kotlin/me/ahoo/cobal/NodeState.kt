package me.ahoo.cobal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

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
    val circuitBreaker: CircuitBreaker

    fun onError(error: CobalError)
    fun onSuccess()
}

internal data class NodeStat(
    val nodeStatus: NodeStatus = NodeStatus.AVAILABLE,
) {
    companion object {
        val Default = NodeStat()
    }
}

class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val failurePolicy: NodeFailurePolicy = NodeFailurePolicy.Default,
    override val circuitBreaker: CircuitBreaker = DefaultCircuitBreaker(),
) : NodeState<NODE> {
    private val stat = AtomicReference(NodeStat.Default)
    private val events = MutableSharedFlow<NodeEvent>()

    @Volatile
    private var recoveryJob: Job? = null

    override val watch: Flow<NodeEvent> = events.asSharedFlow()

    override val status: NodeStatus
        get() = when (circuitBreaker.state) {
            CircuitBreakerState.OPEN -> NodeStatus.CIRCUIT_OPEN
            CircuitBreakerState.HALF_OPEN -> NodeStatus.CIRCUIT_HALF_OPEN
            CircuitBreakerState.CLOSED -> stat.get().nodeStatus
        }

    override fun onError(error: CobalError) {
        val decision = failurePolicy.evaluate(error)
        val cbTransition = circuitBreaker.onError()

        if (decision != null) {
            stat.updateAndGet { it.copy(nodeStatus = NodeStatus.UNAVAILABLE) }
            events.tryEmit(NodeEvent.MarkedUnavailable(node.id, decision.recoverAt))
            scheduleRecovery(decision.recoverAt)
        }

        when (cbTransition) {
            is CircuitBreakerTransition.Opened,
            is CircuitBreakerTransition.ReHalfOpened,
            -> {
                events.tryEmit(NodeEvent.CircuitOpened(node.id))
                circuitBreaker.recoverAt?.let { scheduleRecovery(it) }
            }
            else -> Unit
        }
    }

    override fun onSuccess() {
        val cbTransition = circuitBreaker.onSuccess()
        val wasUnavailable = stat.get().nodeStatus == NodeStatus.UNAVAILABLE

        if (cbTransition != null || wasUnavailable) {
            recoveryJob?.cancel()
            stat.updateAndGet { NodeStat.Default }
            events.tryEmit(NodeEvent.Recovered(node.id))
        }
    }

    private fun scheduleRecovery(recoverAt: Instant) {
        recoveryJob?.cancel()
        val duration = Duration.between(Instant.now(), recoverAt)
        if (duration.isNegative || duration.isZero) {
            recover()
            return
        }
        recoveryJob = scope.launch {
            delay(duration.toMillis())
            recover()
        }
    }

    private fun recover() {
        val cbTransition = circuitBreaker.tryRecover()
        val wasUnavailable = stat.get().nodeStatus == NodeStatus.UNAVAILABLE

        if (wasUnavailable) {
            stat.updateAndGet { NodeStat.Default }
            events.tryEmit(NodeEvent.Recovered(node.id))
        }

        if (cbTransition is CircuitBreakerTransition.HalfOpened) {
            events.tryEmit(NodeEvent.CircuitHalfOpen(node.id))
        }
    }
}
