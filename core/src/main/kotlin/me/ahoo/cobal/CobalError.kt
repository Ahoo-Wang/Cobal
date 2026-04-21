package me.ahoo.cobal

open class CobalError(
    message: String?,
    override val cause: Throwable?
) : Exception(message, cause)
