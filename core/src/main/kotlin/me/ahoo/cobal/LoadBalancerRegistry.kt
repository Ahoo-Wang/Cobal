package me.ahoo.cobal

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for [LoadBalancer] instances.
 *
 * Typically used for tenant-scoped caching where each tenant gets its own load balancer.
 */
interface LoadBalancerRegistry {
    /** Returns an existing load balancer or creates one via [factory]. */
    fun <NODE : Node> getOrCreate(id: LoadBalancerId, factory: () -> LoadBalancer<NODE>): LoadBalancer<NODE>

    /** Returns the load balancer for [id], or null if not found. */
    fun <NODE : Node> get(id: LoadBalancerId): LoadBalancer<NODE>?

    /** Removes and returns the load balancer for [id]. */
    fun remove(id: LoadBalancerId): LoadBalancer<*>?

    /** Returns whether a load balancer with [id] exists. */
    fun contains(id: LoadBalancerId): Boolean
}

/** [LoadBalancerRegistry] backed by a [ConcurrentHashMap]. */
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
