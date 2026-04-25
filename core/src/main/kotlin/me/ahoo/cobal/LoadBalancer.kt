package me.ahoo.cobal

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
