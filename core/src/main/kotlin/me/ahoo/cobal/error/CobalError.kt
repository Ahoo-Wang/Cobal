package me.ahoo.cobal.error

/**
 * Base error for all Cobal load balancing failures.
 *
 * Hierarchy: [NodeError] → [RetriableError] subtypes / non-retriable subtypes, [AllNodesUnavailableError].
 */
open class CobalError(
    message: String?,
    override val cause: Throwable?,
) : RuntimeException(message, cause)

/**
 * Marker interface for errors that warrant a retry on another node.
 *
 * Retriable errors trigger [NodeState] transitions that temporarily mark a node unavailable.
 */
interface RetriableError
