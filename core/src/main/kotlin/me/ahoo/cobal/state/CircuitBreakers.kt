package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
import io.github.resilience4j.kotlin.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.NodeId
import java.time.Duration

val DEFAULT_CIRCUIT_BREAKER_CONFIG: CircuitBreakerConfig = CircuitBreakerConfig {
    failureRateThreshold(100.0f)
        .slidingWindowType(SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(5)
        .minimumNumberOfCalls(5)
        .waitDurationInOpenState(Duration.ofSeconds(60))
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .ignoreExceptions(InvalidRequestError::class.java)
        .build()
}

fun defaultCircuitBreaker(nodeId: NodeId): CircuitBreaker = CircuitBreaker.of(
    nodeId,
    DEFAULT_CIRCUIT_BREAKER_CONFIG
)
