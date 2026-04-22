package me.ahoo.cobal

import java.util.concurrent.ConcurrentHashMap

interface LoadBalancerRegistry {
    fun <NODE : Node> getOrCreate(id: LoadBalancerId, factory: () -> LoadBalancer<NODE>): LoadBalancer<NODE>

    fun <NODE : Node> get(id: LoadBalancerId): LoadBalancer<NODE>?

    fun remove(id: LoadBalancerId): LoadBalancer<*>?

    fun contains(id: LoadBalancerId): Boolean
}

class DefaultLoadBalancerRegistry : LoadBalancerRegistry {
    private val registry = ConcurrentHashMap<LoadBalancerId, LoadBalancer<*>>()

    @Suppress("UNCHECKED_CAST")
    override fun <NODE : Node> getOrCreate(id: LoadBalancerId, factory: () -> LoadBalancer<NODE>): LoadBalancer<NODE> {
        return registry.computeIfAbsent(id) { _ ->
            factory()
        } as LoadBalancer<NODE>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <NODE : Node> get(id: LoadBalancerId): LoadBalancer<NODE>? {
        return registry[id] as LoadBalancer<NODE>?
    }

    override fun remove(id: LoadBalancerId): LoadBalancer<*>? {
        return registry.remove(id)
    }

    override fun contains(id: LoadBalancerId): Boolean {
        return registry.containsKey(id)
    }
}
