package me.ahoo.cobal

import java.time.Instant

data class NodeFailureDecision(
    val recoverAt: Instant,
    val error: CobalError,
)

fun interface NodeFailurePolicy {
    fun evaluate(error: CobalError): NodeFailureDecision?

    companion object {
        val Default = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30), error)
                else -> null
            }
        }
    }
}
