package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AbstractLoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeId
import me.ahoo.cobal.NodeState

class WeightedRoundRobinLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : AbstractLoadBalancer<NODE>(id, states) {

    private var currentIndex = 0
    private var currentWeight: Int
    private val maxWeight: Int = states.maxOf { it.node.weight }
    private val weightMap: Map<NodeId, Int> = states.associate { it.node.id to it.node.weight }

    init {
        currentWeight = maxWeight
    }

    @Synchronized
    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        while (true) {
            currentIndex = (currentIndex + 1) % available.size
            if (currentIndex == 0) {
                currentWeight--
                if (currentWeight <= 0) {
                    currentWeight = maxWeight
                }
            }
            val candidate = available[currentIndex]
            val weight = weightMap[candidate.node.id] ?: return candidate
            if (weight >= currentWeight) {
                return candidate
            }
        }
    }
}
