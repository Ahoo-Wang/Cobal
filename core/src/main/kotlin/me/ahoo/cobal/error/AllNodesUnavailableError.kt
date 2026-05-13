package me.ahoo.cobal.error

import me.ahoo.cobal.LoadBalancerId

/**
 * Thrown by [me.ahoo.cobal.LoadBalancer.execute] when all retry attempts are exhausted.
 *
 * Per-attempt failures (each a [NodeError]) are attached as suppressed exceptions so callers
 * can diagnose which nodes rejected the request and why.
 */
class AllNodesUnavailableError(
    val loadBalancerId: LoadBalancerId,
    attemptFailures: List<NodeError> = emptyList(),
) : CobalError("All nodes unavailable in load balancer: $loadBalancerId", attemptFailures.lastOrNull()) {
    init {
        // The most recent failure is the `cause`; earlier ones are surfaced via suppressed for full diagnostics.
        attemptFailures.dropLast(1).forEach { addSuppressed(it) }
    }
}
