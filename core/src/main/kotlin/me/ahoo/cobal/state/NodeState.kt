package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import me.ahoo.cobal.Node

/**
 * Bridges a [Node] with a Resilience4j [CircuitBreaker] for health tracking.
 *
 * A node is [available] when its weight is positive **and** its circuit breaker permits calls.
 * Delegates all [CircuitBreaker] methods to the underlying instance via Kotlin `by` delegation.
 */
interface NodeState<NODE : Node> : AvailableCapable, CircuitBreaker {
    val node: NODE
    val circuitBreaker: CircuitBreaker

    /** `true` when [node] has positive weight and [circuitBreaker] is in a non-OPEN state. */
    override val available: Boolean
        get() = node.weight > 0 && circuitBreaker.state.available
}

/** Default [NodeState] that delegates [CircuitBreaker] to the provided instance. */
class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    override val circuitBreaker: CircuitBreaker = defaultCircuitBreaker(node.id),
) : NodeState<NODE>, CircuitBreaker by circuitBreaker
