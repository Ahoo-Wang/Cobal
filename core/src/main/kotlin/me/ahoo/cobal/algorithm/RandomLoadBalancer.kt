package me.ahoo.cobal.algorithm

import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeId
import me.ahoo.cobal.NodeState
import java.util.concurrent.ThreadLocalRandom

class AllNodesUnavailableException(val loadBalancerId: LoadBalancerId) : RuntimeException(
    "All nodes unavailable in load balancer: $loadBalancerId"
)

class RandomLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val nodes: List<NODE>
) : LoadBalancer<NODE> {

    private val nodeStates: Map<NodeId, NodeState<NODE>> = nodes.associate {
        it.id to DefaultNodeState(it)
    }

    private fun availableNodes(): List<NodeState<NODE>> {
        return nodeStates.values.filter { it.available }
    }

    override fun choose(): NodeState<NODE> {
        val available = availableNodes()
        if (available.isEmpty()) {
            throw AllNodesUnavailableException(id)
        }
        return available[ThreadLocalRandom.current().nextInt(available.size)]
    }
}
