package me.ahoo.cobal

interface LoadBalancerRegistry {
    fun <NODE : Node> getOrCreate(id: LoadBalancerId, factory: () -> LoadBalancer<NODE>): LoadBalancer<NODE>

    fun <NODE : Node> get(id: LoadBalancerId): LoadBalancer<NODE>?

    fun remove(id: LoadBalancerId): LoadBalancer<*>?

    fun contains(id: LoadBalancerId): Boolean
}

class DefaultLoadBalancerRegistry : LoadBalancerRegistry {
    private val registry = java.util.concurrent.ConcurrentHashMap<LoadBalancerId, LoadBalancer<*>>()

    @Suppress("UNCHECKED_CAST")
    override fun <NODE : Node> getOrCreate(id: LoadBalancerId, factory: () -> LoadBalancer<NODE>): LoadBalancer<NODE> {
        registry[id]?.let { return it as LoadBalancer<NODE> }
        return synchronized(this) {
            registry[id]?.let { return@synchronized it as LoadBalancer<NODE> }
            factory().also { registry[id] = it }
        }
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
