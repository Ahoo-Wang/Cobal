package me.ahoo.cobal

import java.time.Duration
import java.time.Instant

data class NodeFailureDecision(
    val recoverAt: Instant,
)

fun interface NodeFailurePolicy {
    fun evaluate(error: CobalError): NodeFailureDecision?

    object Default : NodeFailurePolicy {
        override fun evaluate(error: CobalError): NodeFailureDecision? {
            return when (error) {
                is RateLimitError -> NodeFailureDecision(Instant.now() + Duration.ofSeconds(30))
                is AuthenticationError -> NodeFailureDecision(Instant.now() + Duration.ofHours(1))
                else -> null
            }
        }
    }
}
