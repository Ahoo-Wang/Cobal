package me.ahoo.cobal.algorithm

import me.ahoo.cobal.*

class WeightedRoundRobinLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val nodes: List<NODE>
) : LoadBalancer<NODE> {

    private val nodeStates: Map<NodeId, NodeState<NODE>> = nodes.associate {
        it.id to DefaultNodeState(it)
    }

    private var currentIndex = 0
    private var currentWeight: Int
    private val maxWeight: Int = nodes.maxOf { it.weight }
    private val weightMap: Map<NodeId, Int> = nodes.associate { it.id to it.weight }

    init {
        currentWeight = maxWeight
    }

    override fun choose(): NodeState<NODE> {
        val available = nodeStates.values.filter { it.available }
        if (available.isEmpty()) {
            throw AllNodesUnavailableException(id)
        }

        while (true) {
            currentIndex = (currentIndex + 1) % available.size
            if (currentIndex == 0) {
                currentWeight--
                if (currentWeight <= 0) {
                    currentWeight = maxWeight
                }
            }
            val candidate = available[currentIndex]
            if (weightMap[candidate.node.id]!! >= currentWeight) {
                return candidate
            }
        }
    }
}