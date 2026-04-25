package me.ahoo.cobal.error

import me.ahoo.cobal.LoadBalancerId

/** All nodes in the load balancer are unavailable. */
class AllNodesUnavailableError(
    val loadBalancerId: LoadBalancerId,
) : CobalError("All nodes unavailable in load balancer: $loadBalancerId", null)
