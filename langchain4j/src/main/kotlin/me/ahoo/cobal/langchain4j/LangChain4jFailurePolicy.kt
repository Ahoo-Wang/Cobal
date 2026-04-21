package me.ahoo.cobal.langchain4j

import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.NodeFailureDecision
import me.ahoo.cobal.NodeFailurePolicy
import me.ahoo.cobal.RateLimitError
import java.time.Duration
import java.time.Instant

val LangChain4jFailurePolicy = NodeFailurePolicy { error ->
    when (error) {
        is RateLimitError -> NodeFailureDecision(
            recoverAt = Instant.now() + Duration.ofSeconds(30)
        )
        is AuthenticationError -> NodeFailureDecision(
            recoverAt = Instant.now() + Duration.ofHours(1)
        )
        else -> null
    }
}
