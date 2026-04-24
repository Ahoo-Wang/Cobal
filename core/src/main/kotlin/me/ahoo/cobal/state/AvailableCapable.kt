package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker

interface AvailableCapable {
    val available: Boolean
}

val CircuitBreaker.State.available: Boolean
    get() {
        return when (this) {
            CircuitBreaker.State.CLOSED,
            CircuitBreaker.State.HALF_OPEN,
            CircuitBreaker.State.DISABLED,
            CircuitBreaker.State.METRICS_ONLY,
            -> true

            else -> false
        }
    }
