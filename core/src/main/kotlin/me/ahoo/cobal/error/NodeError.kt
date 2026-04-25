package me.ahoo.cobal.error

import me.ahoo.cobal.NodeId

/**
 * Error originating from a specific [nodeId].
 *
 * Converted from framework-specific exceptions by [NodeErrorConverter] during [LoadBalancer.execute] retry flow.
 */
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

/** HTTP 401/403 — invalid credentials. Not retriable; produces no [NodeState] change. */
class AuthenticationError(nodeId: NodeId, cause: Throwable?) :
    NodeError(nodeId, "Auth failed [$nodeId]", cause)

/** HTTP 400 — malformed request. Not retriable. Ignored by [DEFAULT_CIRCUIT_BREAKER_CONFIG]. */
class InvalidRequestError(nodeId: NodeId, cause: Throwable?) :
    NodeError(nodeId, "Invalid request [$nodeId]", cause)
