package me.ahoo.cobal

abstract class AbstractLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    final override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        return doChoose(available)
    }

    protected abstract fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE>
}
