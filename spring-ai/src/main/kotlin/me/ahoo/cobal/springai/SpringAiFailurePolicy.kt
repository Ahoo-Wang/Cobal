package me.ahoo.cobal.springai

import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.NodeFailureDecision
import me.ahoo.cobal.NodeFailurePolicy
import java.time.Duration
import java.time.Instant

val SpringAiFailurePolicy = NodeFailurePolicy { error ->
    when (error.category) {
        ErrorCategory.RATE_LIMITED -> NodeFailureDecision(
            recoverAt = Instant.now() + Duration.ofSeconds(30)
        )
        ErrorCategory.AUTHENTICATION -> NodeFailureDecision(
            recoverAt = Instant.now() + Duration.ofHours(1)
        )
        else -> null
    }
}
