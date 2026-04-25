package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
import io.github.resilience4j.kotlin.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.NodeId
import me.ahoo.cobal.error.InvalidRequestError
import java.time.Duration

/**
 * Default circuit breaker configuration for LLM endpoint nodes.
 *
 * Uses a 100% failure rate threshold so that transient successes don't prevent circuit opening.
 * Opens after 5 consecutive failures within a count-based sliding window.
 * Automatically transitions to half-open after 60 seconds.
 * [InvalidRequestError] (400) is ignored — bad requests don't indicate node health issues.
 */
val DEFAULT_CIRCUIT_BREAKER_CONFIG: CircuitBreakerConfig = CircuitBreakerConfig {
    failureRateThreshold(100.0f)
        .slidingWindowType(SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(5)
        .minimumNumberOfCalls(5)
        .waitDurationInOpenState(Duration.ofSeconds(60))
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .ignoreExceptions(InvalidRequestError::class.java)
}

/** Creates a [CircuitBreaker] for the given [nodeId] using [DEFAULT_CIRCUIT_BREAKER_CONFIG]. */
fun defaultCircuitBreaker(nodeId: NodeId): CircuitBreaker = CircuitBreaker.of(
    nodeId,
    DEFAULT_CIRCUIT_BREAKER_CONFIG
)
