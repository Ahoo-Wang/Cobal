package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
import io.github.resilience4j.kotlin.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.NodeId
import me.ahoo.cobal.error.InvalidRequestError
import java.time.Duration

/**
 * Default circuit breaker configuration tuned for LLM endpoint nodes.
 *
 * - 100% failure rate threshold: prevents transient successes from masking persistent failures.
 * - Count-based sliding window of 5: opens after 5 consecutive failures.
 * - 60s open-state wait: aligns with typical rate-limit reset windows.
 * - [InvalidRequestError] ignored: 400 errors reflect caller issues, not endpoint health.
 * - Slow-call detection explicitly disabled (`slowCallRateThreshold = 100%`, threshold = 15min):
 *   LLM tasks are inherently long-running; call duration does not indicate endpoint health.
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
        .slowCallRateThreshold(100f)
        .slowCallDurationThreshold(Duration.ofMinutes(15))
}

/** Creates a [CircuitBreaker] for the given [nodeId] using [DEFAULT_CIRCUIT_BREAKER_CONFIG]. */
fun defaultCircuitBreaker(nodeId: NodeId): CircuitBreaker = CircuitBreaker.of(
    nodeId,
    DEFAULT_CIRCUIT_BREAKER_CONFIG
)
