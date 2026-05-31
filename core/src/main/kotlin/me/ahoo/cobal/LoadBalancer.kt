package me.ahoo.cobal

import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.NodeError
import me.ahoo.cobal.error.NodeErrorConverter
import me.ahoo.cobal.error.isNonRetriable
import me.ahoo.cobal.error.throwIfNonRetriable
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

/**
 * Executes [block] against a selected node's model with automatic retry on failure.
 *
 * On each iteration: acquires circuit breaker permission via [NodeState.tryAcquirePermission],
 * calls [block], records success/failure with timing.
 *
 * Permission denials (HALF_OPEN throttle, etc.) do **not** consume an attempt — they are
 * bounded separately by [Node] count to prevent livelock. Only successful invocations of
 * [block] count toward [maxAttempts].
 *
 * Explicit non-retriable errors are thrown immediately without retry — bad requests
 * or authentication failures won't succeed on another node.
 *
 * @param NODE the model node type
 * @param MODEL the framework-specific model type
 * @param R the result type
 * @param nodeErrorConverter translates framework exceptions to [NodeError]
 * @param maxAttempts maximum number of [block] invocations. `0` (default) auto-sizes to the
 *   number of available nodes at call entry. Must be `>= 0`.
 * @param block the operation to execute against the selected node's model
 * @throws AllNodesUnavailableError if all attempts are exhausted; each [NodeError] from a
 *   failed attempt is attached via [Throwable.addSuppressed] (with the latest as `cause`)
 * @throws me.ahoo.cobal.error.NodeError immediately for explicit non-retriable errors,
 *   without retry
 */
@Suppress("TooGenericExceptionCaught", "LoopWithTooManyJumpStatements")
inline fun <NODE : ModelNode<MODEL>, MODEL, R : Any> LoadBalancer<NODE>.execute(
    nodeErrorConverter: NodeErrorConverter,
    maxAttempts: Int = 0,
    block: (MODEL) -> R,
): R {
    require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
    val effectiveMax = if (maxAttempts > 0) maxAttempts else availableStates.size.coerceAtLeast(1)
    val rejectionBudget = states.size
    val failures = mutableListOf<NodeError>()
    var attempts = 0
    var rejections = 0
    while (attempts < effectiveMax) {
        if (availableStates.isEmpty()) break
        val candidate = choose()
        if (!candidate.tryAcquirePermission()) {
            rejections++
            if (rejections >= rejectionBudget) break
            continue
        }
        val start = candidate.currentTimestamp
        try {
            val result = block(candidate.node.model)
            val duration = candidate.currentTimestamp - start
            candidate.onResult(duration, candidate.timestampUnit, result)
            return result
        } catch (e: Exception) {
            val nodeError = nodeErrorConverter.convert(candidate.node.id, e)
            if (nodeError.isNonRetriable) {
                candidate.releasePermission()
                nodeError.throwIfNonRetriable()
            }
            val duration = candidate.currentTimestamp - start
            candidate.onError(duration, candidate.timestampUnit, nodeError)
            failures.add(nodeError)
        }
        attempts++
    }
    throw AllNodesUnavailableError(id, failures)
}
