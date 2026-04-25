package me.ahoo.cobal.error

import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.NodeId

/** Base error for all Cobal load balancing failures. */
abstract class CobalError(
    message: String?,
    override val cause: Throwable?,
) : RuntimeException(message, cause)

/** Marker interface for errors that warrant a retry on another node. */
interface RetriableError

/** Error originating from a specific [nodeId]. */
open class NodeError(
    val nodeId: NodeId,
    message: String?,
    cause: Throwable?,
) : CobalError(message, cause)

/** HTTP 429 Too Many Requests. */
class RateLimitError(nodeId: NodeId, cause: Throwable?) :
    NodeError(nodeId, "Rate limited [$nodeId]", cause), RetriableError

/** HTTP 5xx server error. */
class ServerError(nodeId: NodeId, cause: Throwable?) :
    NodeError(nodeId, "Server error [$nodeId]", cause), RetriableError

/** Request timed out. */
class TimeoutError(nodeId: NodeId, cause: Throwable?) :
    NodeError(nodeId, "Timeout [$nodeId]", cause), RetriableError

/** Network-level failure (connection refused, DNS error, etc.). */
class NetworkError(nodeId: NodeId, cause: Throwable?) :
    NodeError(nodeId, "Network error [$nodeId]", cause), RetriableError

/** HTTP 401/403 — invalid credentials. Not retriable. */
class AuthenticationError(nodeId: NodeId, cause: Throwable?) :
    NodeError(nodeId, "Auth failed [$nodeId]", cause)

/** HTTP 400 — malformed request. Not retriable. Ignored by circuit breaker. */
class InvalidRequestError(nodeId: NodeId, cause: Throwable?) :
    NodeError(nodeId, "Invalid request [$nodeId]", cause)

/** All nodes in the load balancer are unavailable. */
class AllNodesUnavailableError(
    val loadBalancerId: LoadBalancerId,
) : CobalError("All nodes unavailable in load balancer: $loadBalancerId", null)
