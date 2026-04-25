package me.ahoo.cobal.error

/** Base error for all Cobal load balancing failures. */
open class CobalError(
    message: String?,
    override val cause: Throwable?,
) : RuntimeException(message, cause)

/** Marker interface for errors that warrant a retry on another node. */
interface RetriableError
