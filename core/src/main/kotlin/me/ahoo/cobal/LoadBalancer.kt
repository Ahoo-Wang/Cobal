package me.ahoo.cobal

import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.NodeErrorConverter
import me.ahoo.cobal.state.NodeState

/** Unique identifier for a [LoadBalancer] instance. */
typealias LoadBalancerId = String

/**
 * Selects a [NodeState] from the pool using a specific algorithm.
 *
 * Implementations choose among [availableStates] via [choose], giving callers
 * direct access to the selected node's circuit breaker for fault tolerance tracking.
 */
interface LoadBalancer<NODE : Node> {
    val id: LoadBalancerId

    /** All node states managed by this load balancer. */
    val states: List<NodeState<NODE>>

    /** States filtered to only those currently available for selection. */
    val availableStates: List<NodeState<NODE>>
        get() = states.filter { it.available }

    /**
     * Selects a node state from [availableStates].
     *
     * @throws me.ahoo.cobal.error.AllNodesUnavailableError if no nodes are available
     */
    fun choose(): NodeState<NODE>
}

@Suppress("TooGenericExceptionCaught")
inline fun <NODE : ModelNode<MODEL>, MODEL, R : Any> LoadBalancer<NODE>.execute(
    nodeErrorConverter: NodeErrorConverter,
    maxAttempts: Int = availableStates.size,
    block: (MODEL) -> R,
): R {
    repeat(maxAttempts) {
        val candidate = choose()
        val acquired = candidate.tryAcquirePermission()
        if (!acquired) {
            return@repeat
        }
        val start = candidate.currentTimestamp
        try {
            val result = block(candidate.node.model)
            val duration = candidate.currentTimestamp - start
            candidate.onResult(duration, candidate.timestampUnit, result)
            return result
        } catch (e: Exception) {
            val nodeError = nodeErrorConverter.convert(candidate.node.id, e)
            val duration = candidate.currentTimestamp - start
            candidate.onError(duration, candidate.timestampUnit, nodeError)
        }
    }
    throw AllNodesUnavailableError(id)
}
