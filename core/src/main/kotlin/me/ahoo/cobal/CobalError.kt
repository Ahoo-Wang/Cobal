package me.ahoo.cobal

open class CobalError(
    message: String?,
    override val cause: Throwable?
) : RuntimeException(message, cause)

class AllNodesUnavailableError(
    val loadBalancerId: LoadBalancerId
) : CobalError(
    "All nodes unavailable in load balancer: $loadBalancerId",
    null
)
