package me.ahoo.cobal.algorithm

import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.state.NodeState
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

/**
 * Selects nodes randomly with probability proportional to their [weight][Node.weight].
 *
 * Uses Vose's Alias Method for O(1) selection after O(n) table construction.
 * The alias table is rebuilt when node availability changes.
 */
class WeightedRandomLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>,
) : AbstractLoadBalancer<NODE>(id, states) {

    private val aliasTableRef = AtomicReference<AliasTable?>(buildAliasTable())

    private class AliasTable(
        val prob: DoubleArray,
        val alias: IntArray,
    )

    override fun onStateChanged() {
        rebuildAliasTable()
    }

    private fun buildAliasTable() = buildAliasTable(availableStates)

    private fun rebuildAliasTable() {
        aliasTableRef.set(buildAliasTable())
    }

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        val table = aliasTableRef.get()
        val random = ThreadLocalRandom.current()
        if (table == null || available.size == 1 || table.prob.size != available.size) {
            return available[random.nextInt(available.size)]
        }
        val slot = random.nextInt(available.size)
        return if (random.nextDouble() < table.prob[slot]) {
            available[slot]
        } else {
            available[table.alias[slot]]
        }
    }

    companion object {
        private fun buildAliasTable(states: List<NodeState<*>>): AliasTable? {
            val n = states.size
            if (n <= 1) return null
            val totalWeight = states.sumOf { it.node.weight.toDouble() }
            if (totalWeight <= 0.0) return null

            val prob = DoubleArray(n)
            val alias = IntArray(n)
            // Normalize weights so each averages 1.0
            val normalizedWeights = DoubleArray(n) { i -> states[i].node.weight.toDouble() * n / totalWeight }

            // Partition into below-average (small) and above-average (large)
            val small = ArrayDeque<Int>()
            val large = ArrayDeque<Int>()

            for (i in 0 until n) {
                if (normalizedWeights[i] < 1.0) small.addLast(i) else large.addLast(i)
            }

            // Pair small with large: small gets its probability, large absorbs the remainder
            while (small.isNotEmpty() && large.isNotEmpty()) {
                val s = small.removeFirst()
                val l = large.removeFirst()
                prob[s] = normalizedWeights[s]
                alias[s] = l
                normalizedWeights[l] += normalizedWeights[s] - 1.0
                if (normalizedWeights[l] < 1.0) small.addLast(l) else large.addLast(l)
            }

            // Remaining entries (floating-point rounding) get probability 1.0
            while (large.isNotEmpty()) {
                prob[large.removeFirst()] = 1.0
            }
            while (small.isNotEmpty()) {
                prob[small.removeFirst()] = 1.0
            }

            return AliasTable(prob, alias)
        }
    }
}
