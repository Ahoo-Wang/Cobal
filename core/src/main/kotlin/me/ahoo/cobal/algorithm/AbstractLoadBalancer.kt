package me.ahoo.cobal.algorithm

import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.state.NodeState
import java.util.concurrent.atomic.AtomicReference

/**
 * Base [LoadBalancer] that caches [availableStates] reactively.
 *
 * Subscribes to each [NodeState]'s circuit breaker state transitions and updates the cached
 * available list via [AtomicReference]. Subclasses implement [doChoose] for algorithm-specific
 * selection and optionally override [onStateChanged] to rebuild internal state.
 */
abstract class AbstractLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>,
) : LoadBalancer<NODE> {
    private val availableStatesRef = AtomicReference(super.availableStates)
    override val availableStates: List<NodeState<NODE>>
        get() = availableStatesRef.get()

    init {
        check(states.isNotEmpty()) {
            "LoadBalancer must have at least one node."
        }
    }

    // Subscribe to state transitions to keep cached availableStates in sync
    init {
        states.forEach { nodeState ->
            nodeState.eventPublisher.onStateTransition {
                val availableStates = super.availableStates
                availableStatesRef.set(availableStates)
                onStateChanged()
            }
        }
    }

    /** Called when any node's circuit breaker state transitions. Override to rebuild cached data. */
    open fun onStateChanged() = Unit

    /**
     * Selects from [availableStates] via [doChoose].
     *
     * @throws AllNodesUnavailableError if no nodes are available
     */
    final override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        return doChoose(available)
    }

    /** Algorithm-specific selection from the current [available] nodes. */
    protected abstract fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE>
}
