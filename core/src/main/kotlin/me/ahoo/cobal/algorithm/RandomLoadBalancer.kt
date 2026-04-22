package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AbstractLoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.state.NodeState
import java.util.concurrent.ThreadLocalRandom

class RandomLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>,
) : AbstractLoadBalancer<NODE>(id, states) {

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        val idx = ThreadLocalRandom.current().nextInt(available.size)
        return available[idx]
    }
}
