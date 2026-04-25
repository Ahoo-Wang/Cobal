package me.ahoo.cobal.error

import me.ahoo.cobal.LoadBalancerId

/** Thrown by [LoadBalancer.execute] when all retry attempts are exhausted. */
class AllNodesUnavailableError(
    val loadBalancerId: LoadBalancerId,
) : CobalError("All nodes unavailable in load balancer: $loadBalancerId", null)
