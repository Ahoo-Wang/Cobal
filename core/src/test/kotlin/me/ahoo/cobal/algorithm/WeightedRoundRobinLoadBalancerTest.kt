package me.ahoo.cobal.algorithm

import me.ahoo.cobal.DefaultNode
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class WeightedRoundRobinLoadBalancerTest {
    @Test
    fun `weighted round robin should respect node weights`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-2", weight = 1)
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(state1, state2))
        // node-1 should be chosen 3 times, node-2 once in a cycle of 4
        val counts = mutableMapOf("node-1" to 0, "node-2" to 0)
        repeat(12) { // 3 cycles
            val chosen = lb.choose()
            counts[chosen.node.id] = counts[chosen.node.id]!! + 1
        }
        counts["node-1"].assert().isEqualTo(9) // 3 * 3
        counts["node-2"].assert().isEqualTo(3) // 3 * 1
    }

    @Test
    fun `choose should skip unavailable node`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-2", weight = 1)
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(state1, state2))

        state2.onError(me.ahoo.cobal.RateLimitError(node2.id, RuntimeException("429")))

        repeat(12) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }

    @Test
    fun `concurrent choose should be thread-safe`() {
        val node1 = DefaultNode("node-1", weight = 3)
        val node2 = DefaultNode("node-2", weight = 1)
        val state1 = DefaultNodeState(node1)
        val state2 = DefaultNodeState(node2)
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(state1, state2))

        val threadCount = 10
        val iterations = 100
        val latch = CountDownLatch(threadCount)
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        counts["node-1"] = AtomicInteger(0)
        counts["node-2"] = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        repeat(threadCount) {
            thread {
                try {
                    repeat(iterations) {
                        val chosen = lb.choose()
                        counts[chosen.node.id]!!.incrementAndGet()
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()

        errorCount.get().assert().isEqualTo(0)
        val total = counts["node-1"]!!.get() + counts["node-2"]!!.get()
        total.assert().isEqualTo(threadCount * iterations)
    }
}
