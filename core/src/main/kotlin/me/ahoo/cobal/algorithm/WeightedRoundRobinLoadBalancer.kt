package me.ahoo.cobal.algorithm

import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.state.NodeState
import java.util.concurrent.atomic.AtomicReference

/**
 * Selects nodes in a smooth weighted round-robin pattern (Nginx-style).
 *
 * On each selection, adds each node's weight to a running counter, picks the node with
 * the highest counter, then subtracts [totalWeight]. This produces an even, deterministic
 * distribution — e.g., weights 5:1:1 yields A A A A A B C over 7 calls.
 */
class WeightedRoundRobinLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>,
) : AbstractLoadBalancer<NODE>(id, states) {

    private val currentWeightsRef = AtomicReference(IntArray(0))
    private val totalWeightRef = AtomicReference(0)

    init {
        rebuildWeights()
    }

    override fun onStateChanged() {
        rebuildWeights()
    }

    private fun rebuildWeights() {
        val available = availableStates
        currentWeightsRef.set(IntArray(available.size))
        totalWeightRef.set(available.sumOf { it.node.weight })
    }

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        if (available.size == 1) return available[0]

        val totalWeight = totalWeightRef.get()
        val weights = currentWeightsRef.get()
        if (weights.size != available.size) {
            return available[0]
        }

        var bestIndex = 0
        var bestWeight = Int.MIN_VALUE
        for (i in available.indices) {
            weights[i] += available[i].node.weight
            if (weights[i] > bestWeight) {
                bestWeight = weights[i]
                bestIndex = i
            }
        }
        weights[bestIndex] -= totalWeight
        return available[bestIndex]
    }
}
