package me.ahoo.cobal

import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.NodeErrorConverter

/**
 * Base class for load-balanced model wrappers.
 *
 * Implements the retry loop: [choose][LoadBalancer.choose] a node → delegate to its model →
 * on failure: convert error via [NodeErrorConverter], record with circuit breaker, retry with next node.
 * After all retries exhausted, throws [AllNodesUnavailableError].
 */
abstract class AbstractLoadBalancedModel<NODE : ModelNode<MODEL>, MODEL>(
    val loadBalancer: LoadBalancer<NODE>,
    protected val nodeErrorConverter: NodeErrorConverter,
) {
    /** Maximum number of retry attempts. Defaults to the number of available nodes. */
    protected open val maxAttempts: Int = loadBalancer.availableStates.size

    /**
     * Executes [block] against a selected node's model with automatic retry on failure.
     *
     * On each attempt: acquires circuit breaker permission, calls [block], records success/failure.
     * Skips nodes whose circuit breaker denies permission.
     *
     * @throws AllNodesUnavailableError if all attempts are exhausted
     */
    @Suppress("TooGenericExceptionCaught")
    protected fun <T : Any> executeWithRetry(block: (MODEL) -> T): T {
        repeat(maxAttempts) {
            val selected = loadBalancer.choose()
            val acquired = selected.tryAcquirePermission()
            if (!acquired) {
                return@repeat
            }
            val start = selected.currentTimestamp
            try {
                val result = block(selected.node.model)
                val duration = selected.currentTimestamp - start
                selected.onResult(duration, selected.timestampUnit, result)
                return result
            } catch (e: Exception) {
                val nodeError = nodeErrorConverter.convert(selected.node.id, e)
                val duration = selected.currentTimestamp - start
                selected.onError(duration, selected.timestampUnit, nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }
}
