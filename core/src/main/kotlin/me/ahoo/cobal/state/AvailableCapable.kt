package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker

/** Indicates whether a component is currently available for use. */
interface AvailableCapable {
    val available: Boolean
}

/** Maps [CircuitBreaker.State] to availability. OPEN is the only unavailable state. */
val CircuitBreaker.State.available: Boolean
    get() = when (this) {
        CircuitBreaker.State.CLOSED,
        CircuitBreaker.State.HALF_OPEN,
        CircuitBreaker.State.DISABLED,
        CircuitBreaker.State.METRICS_ONLY,
        -> true

        else -> false
    }
