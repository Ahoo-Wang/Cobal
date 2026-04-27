package me.ahoo.cobal.algorithm

import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.state.NodeState
import java.util.concurrent.atomic.AtomicInteger

/** Selects nodes in strict round-robin order using a lock-free [AtomicInteger] index. */
class RoundRobinLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : AbstractLoadBalancer<NODE>(id, states) {

    private val index = AtomicInteger(0)

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        val idx = Math.floorMod(index.getAndIncrement(), available.size)
        return available[idx]
    }
}
