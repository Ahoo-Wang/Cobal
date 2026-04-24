package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import me.ahoo.cobal.Node

interface NodeState<NODE : Node> : AvailableCapable, CircuitBreaker {
    val node: NODE
    val circuitBreaker: CircuitBreaker
    override val available: Boolean
        get() = circuitBreaker.state.available
}

class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    override val circuitBreaker: CircuitBreaker = defaultCircuitBreaker(node.id),
) : NodeState<NODE>, CircuitBreaker by circuitBreaker
