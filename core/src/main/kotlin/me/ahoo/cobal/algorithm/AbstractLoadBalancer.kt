package me.ahoo.cobal.algorithm

import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.state.NodeState
import java.util.concurrent.atomic.AtomicReference

abstract class AbstractLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>,
) : LoadBalancer<NODE> {
    private val availableStatesRef = AtomicReference(super.availableStates)
    override val availableStates: List<NodeState<NODE>>
        get() = availableStatesRef.get()

    init {
        states.forEach { nodeState ->
            nodeState.eventPublisher.onStateTransition {
                val availableStates = super.availableStates
                availableStatesRef.set(availableStates)
                onStateChanged()
            }
        }
    }

    open fun onStateChanged() = Unit

    final override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        return doChoose(available)
    }

    protected abstract fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE>
}
