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

    fun onError(error: CobalError)
    fun onSuccess()
}

internal data class NodeStat(
    val failureCount: Int = 0,
    val circuitOpened: Boolean = false,
    val status: NodeStatus = NodeStatus.AVAILABLE,
)

class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val failurePolicy: NodeFailurePolicy = NodeFailurePolicy.Default,
    private val circuitOpenThreshold: Int = 5,
) : NodeState<NODE> {
    private val stat = AtomicReference(NodeStat())
    private val events = MutableSharedFlow<NodeEvent>()

    @Volatile
    private var recoveryJob: Job? = null

    override val watch: Flow<NodeEvent> = events.asSharedFlow()

    override val status: NodeStatus
        get() = stat.get().status

    override fun onError(error: CobalError) {
        val decision = failurePolicy.evaluate(error)
        var becameCircuitOpen = false
        var recoveredAt: Instant? = null

        stat.updateAndGet { current ->
            val newCount = current.failureCount + 1
            val newOpened = newCount >= circuitOpenThreshold
            val newStatus = when {
                current.status == NodeStatus.CIRCUIT_HALF_OPEN -> NodeStatus.CIRCUIT_OPEN
                newOpened -> NodeStatus.CIRCUIT_OPEN
                decision != null -> NodeStatus.UNAVAILABLE
                else -> current.status
            }
            becameCircuitOpen = newOpened && !current.circuitOpened
            recoveredAt = decision?.recoverAt
            NodeStat(
                failureCount = newCount,
                circuitOpened = newOpened || current.circuitOpened,
                status = newStatus,
            )
        }

        if (recoveredAt != null) {
            events.tryEmit(NodeEvent.MarkedUnavailable(node.id, recoveredAt!!))
            scheduleRecovery(recoveredAt!!)
        }
        if (becameCircuitOpen) {
            events.tryEmit(NodeEvent.CircuitOpened(node.id))
        }
    }

    override fun onSuccess() {
        var recovered = false
        stat.updateAndGet { current ->
            when (current.status) {
                NodeStatus.CIRCUIT_HALF_OPEN,
                NodeStatus.UNAVAILABLE -> {
                    recovered = true
                    NodeStat()
                }
                else -> current.copy(failureCount = 0)
            }
        }
        if (recovered) {
            recoveryJob?.cancel()
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
        var transitionedTo = stat.updateAndGet { current ->
            when (current.status) {
                NodeStatus.UNAVAILABLE -> current.copy(status = NodeStatus.AVAILABLE)
                NodeStatus.CIRCUIT_OPEN -> current.copy(status = NodeStatus.CIRCUIT_HALF_OPEN)
                else -> current
            }
        }.status
        when (transitionedTo) {
            NodeStatus.AVAILABLE -> events.tryEmit(NodeEvent.Recovered(node.id))
            NodeStatus.CIRCUIT_HALF_OPEN -> events.tryEmit(NodeEvent.CircuitHalfOpen(node.id))
            else -> Unit
        }
    }
}
