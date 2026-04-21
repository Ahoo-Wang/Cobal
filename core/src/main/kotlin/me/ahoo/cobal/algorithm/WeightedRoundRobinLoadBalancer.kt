package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeId
import me.ahoo.cobal.NodeState

class WeightedRoundRobinLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    private var currentIndex = 0
    private var currentWeight: Int
    private val maxWeight: Int = states.maxOf { it.node.weight }
    private val weightMap: Map<NodeId, Int> = states.associate { it.node.id to it.node.weight }

    init {
        currentWeight = maxWeight
    }

    override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
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
