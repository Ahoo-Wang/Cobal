package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeId
import me.ahoo.cobal.NodeState
import java.util.concurrent.atomic.AtomicInteger

class RoundRobinLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val nodes: List<NODE>
) : LoadBalancer<NODE> {

    private val nodeStates: Map<NodeId, NodeState<NODE>> = nodes.associate {
        it.id to DefaultNodeState(it)
    }

    private val index = AtomicInteger(0)

    override fun choose(): NodeState<NODE> {
        val available = nodeStates.values.filter { it.available }
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        val startIndex = index.getAndIncrement() % available.size
        return available[startIndex]
    }
}
