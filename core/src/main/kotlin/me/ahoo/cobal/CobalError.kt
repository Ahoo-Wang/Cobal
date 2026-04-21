package me.ahoo.cobal

import java.time.Instant

abstract class CobalError(
    message: String?,
    override val cause: Throwable?
) : RuntimeException(message, cause)

interface RetriableError

open class NodeError(
    val nodeId: NodeId,
    message: String?,
    cause: Throwable?
) : CobalError(message, cause)

class RateLimitError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Rate limited [$nodeId]", cause), RetriableError
class ServerError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Server error [$nodeId]", cause), RetriableError
class TimeoutError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Timeout [$nodeId]", cause), RetriableError
class NetworkError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Network error [$nodeId]", cause), RetriableError
class AuthenticationError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Auth failed [$nodeId]", cause)
class InvalidRequestError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Invalid request [$nodeId]", cause)

class AllNodesUnavailableError(
    val loadBalancerId: LoadBalancerId
) : CobalError("All nodes unavailable in load balancer: $loadBalancerId", null)

data class NodeFailureDecision(val recoverAt: Instant)

fun interface NodeFailurePolicy {
    fun evaluate(error: CobalError): NodeFailureDecision?

    companion object {
        val Default = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
    }
}