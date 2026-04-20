package me.ahoo.cobal

class AllNodesUnavailableException(val loadBalancerId: LoadBalancerId) : RuntimeException(
    "All nodes unavailable in load balancer: $loadBalancerId"
)