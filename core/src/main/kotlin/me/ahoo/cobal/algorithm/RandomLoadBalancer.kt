package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.util.concurrent.ThreadLocalRandom

class RandomLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        return available[ThreadLocalRandom.current().nextInt(available.size)]
    }
}