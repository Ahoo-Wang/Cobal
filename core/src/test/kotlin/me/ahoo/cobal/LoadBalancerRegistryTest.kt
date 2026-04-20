package me.ahoo.cobal

import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancerRegistryTest {
    @Test
    fun `getOrCreate should create and cache instance`() {
        val registry = LoadBalancerRegistry()
        val lb: LoadBalancer<SimpleNode> = registry.getOrCreate("lb-1") {
            RandomLoadBalancer("lb-1", listOf(SimpleNode("node-1")))
        }
        val lb2: LoadBalancer<SimpleNode> = registry.getOrCreate("lb-1") { throw RuntimeException("should not be called") }
        lb.assert().isSameAs(lb2)
    }

    @Test
    fun `remove should evict cached instance`() {
        val registry = LoadBalancerRegistry()
        registry.getOrCreate<SimpleNode>("lb-1") {
            RandomLoadBalancer("lb-1", listOf(SimpleNode("node-1")))
        }
        registry.remove("lb-1")
        registry.contains("lb-1").assert().isFalse()
    }

    @Test
    fun `contains should return true for existing id`() {
        val registry = LoadBalancerRegistry()
        registry.getOrCreate<SimpleNode>("lb-1") {
            RandomLoadBalancer("lb-1", listOf(SimpleNode("node-1")))
        }
        registry.contains("lb-1").assert().isTrue()
        registry.contains("lb-2").assert().isFalse()
    }
}