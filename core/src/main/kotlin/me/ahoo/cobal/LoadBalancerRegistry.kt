package me.ahoo.cobal

class LoadBalancerRegistry {
    private val registry = java.util.concurrent.ConcurrentHashMap<LoadBalancerId, LoadBalancer<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <NODE : Node> getOrCreate(id: LoadBalancerId, factory: () -> LoadBalancer<NODE>): LoadBalancer<NODE> {
        registry[id]?.let { return it as LoadBalancer<NODE> }
        return synchronized(this) {
            registry[id]?.let { return@synchronized it as LoadBalancer<NODE> }
            factory().also { registry[id] = it }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <NODE : Node> get(id: LoadBalancerId): LoadBalancer<NODE>? {
        return registry[id] as LoadBalancer<NODE>?
    }

    fun remove(id: LoadBalancerId): LoadBalancer<*>? {
        return registry.remove(id)
    }

    fun contains(id: LoadBalancerId): Boolean {
        return registry.containsKey(id)
    }
}
