package me.ahoo.cobal

import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.state.NodeState

abstract class AbstractLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    final override val states: List<NodeState<NODE>> = states.toList()

    final override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        return doChoose(available)
    }

    protected abstract fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE>
}
