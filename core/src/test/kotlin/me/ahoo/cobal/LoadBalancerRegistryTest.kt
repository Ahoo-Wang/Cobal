package me.ahoo.cobal

import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancerRegistryTest {
    @Test
    fun `getOrCreate should create and cache instance`() {
        val registry = DefaultLoadBalancerRegistry()
        val node1 = SimpleNode("node-1")
        val state1 = DefaultNodeState(node1)
        val lb: LoadBalancer<SimpleNode> = registry.getOrCreate("lb-1") {
            RandomLoadBalancer("lb-1", listOf(state1))
        }
        val lb2: LoadBalancer<SimpleNode> = registry.getOrCreate("lb-1") {
            throw IllegalStateException("should not be called")
        }
        lb.assert().isSameAs(lb2)
    }

    @Test
    fun `remove should evict cached instance`() {
        val registry = DefaultLoadBalancerRegistry()
        val node1 = SimpleNode("node-1")
        val state1 = DefaultNodeState(node1)
        registry.getOrCreate<SimpleNode>("lb-1") {
            RandomLoadBalancer("lb-1", listOf(state1))
        }
        registry.remove("lb-1")
        registry.contains("lb-1").assert().isFalse()
    }

    @Test
    fun `contains should return true for existing id`() {
        val registry = DefaultLoadBalancerRegistry()
        val node1 = SimpleNode("node-1")
        val state1 = DefaultNodeState(node1)
        registry.getOrCreate<SimpleNode>("lb-1") {
            RandomLoadBalancer("lb-1", listOf(state1))
        }
        registry.contains("lb-1").assert().isTrue()
        registry.contains("lb-2").assert().isFalse()
    }
}
