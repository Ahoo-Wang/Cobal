package me.ahoo.cobal

import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancerRegistryTest {

    private fun createTestLoadBalancer(): LoadBalancer<DefaultNode> {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)
        return RandomLoadBalancer("test-lb", listOf(state))
    }

    @Test
    fun `getOrCreate should create and cache instance`() {
        val registry = DefaultLoadBalancerRegistry()
        val lb = createTestLoadBalancer()

        val first = registry.getOrCreate("lb-1") { lb }
        val second = registry.getOrCreate("lb-1") { createTestLoadBalancer() }

        first.assert().isSameAs(lb)
        second.assert().isSameAs(first)
    }

    @Test
    fun `getOrCreate should call factory only once`() {
        val registry = DefaultLoadBalancerRegistry()
        var callCount = 0

        registry.getOrCreate("lb-1") {
            callCount++
            createTestLoadBalancer()
        }
        registry.getOrCreate("lb-1") {
            callCount++
            createTestLoadBalancer()
        }

        callCount.assert().isEqualTo(1)
    }

    @Test
    fun `get should return null for non-existent id`() {
        val registry = DefaultLoadBalancerRegistry()

        val result: LoadBalancer<DefaultNode>? = registry.get("nonexistent")

        result.assert().isNull()
    }

    @Test
    fun `get should return cached instance`() {
        val registry = DefaultLoadBalancerRegistry()
        val lb = createTestLoadBalancer()

        registry.getOrCreate("lb-1") { lb }
        val result: LoadBalancer<DefaultNode>? = registry.get("lb-1")

        result.assert().isSameAs(lb)
    }

    @Test
    fun `remove should evict cached instance`() {
        val registry = DefaultLoadBalancerRegistry()
        val lb = createTestLoadBalancer()

        registry.getOrCreate("lb-1") { lb }
        registry.remove("lb-1")

        registry.contains("lb-1").assert().isFalse()
        val result: LoadBalancer<DefaultNode>? = registry.get("lb-1")
        result.assert().isNull()
    }

    @Test
    fun `contains should return correct values`() {
        val registry = DefaultLoadBalancerRegistry()

        registry.contains("lb-1").assert().isFalse()

        registry.getOrCreate("lb-1") { createTestLoadBalancer() }
        registry.contains("lb-1").assert().isTrue()

        registry.remove("lb-1")
        registry.contains("lb-1").assert().isFalse()
    }
}
