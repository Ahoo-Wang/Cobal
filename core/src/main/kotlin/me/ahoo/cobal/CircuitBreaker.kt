package me.ahoo.cobal

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

sealed interface CircuitBreakerTransition {
    data object Opened : CircuitBreakerTransition
    data object ReHalfOpened : CircuitBreakerTransition
    data object HalfOpened : CircuitBreakerTransition
    data object Closed : CircuitBreakerTransition
}

interface CircuitBreaker {
    val state: CircuitBreakerState
    val recoverAt: Instant?

    fun onError(): CircuitBreakerTransition?
    fun onSuccess(): CircuitBreakerTransition?
    fun tryRecover(): CircuitBreakerTransition?
}

internal data class CircuitBreakerStat(
    val failureCount: Int = 0,
    val state: CircuitBreakerState = CircuitBreakerState.CLOSED,
    val openedAt: Instant? = null,
) {
    companion object {
        val Default = CircuitBreakerStat()
    }
}

class DefaultCircuitBreaker(
    private val threshold: Int = 5,
    private val recoveryDuration: Duration = Duration.ofSeconds(60),
) : CircuitBreaker {
    private val stat = AtomicReference(CircuitBreakerStat.Default)

    override val state: CircuitBreakerState
        get() = stat.get().state

    override val recoverAt: Instant?
        get() = stat.get().openedAt?.plus(recoveryDuration)

    override fun onError(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            when (current.state) {
                CircuitBreakerState.HALF_OPEN -> {
                    transition = CircuitBreakerTransition.ReHalfOpened
                    current.copy(state = CircuitBreakerState.OPEN, openedAt = Instant.now(), failureCount = current.failureCount + 1)
                }

                CircuitBreakerState.CLOSED -> {
                    val newCount = current.failureCount + 1
                    if (newCount >= threshold) {
                        transition = CircuitBreakerTransition.Opened
                        current.copy(state = CircuitBreakerState.OPEN, openedAt = Instant.now(), failureCount = newCount)
                    } else {
                        current.copy(failureCount = newCount)
                    }
                }

                CircuitBreakerState.OPEN -> current
            }
        }
        return transition
    }

    override fun onSuccess(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            when (current.state) {
                CircuitBreakerState.HALF_OPEN -> {
                    transition = CircuitBreakerTransition.Closed
                    CircuitBreakerStat.Default
                }

                CircuitBreakerState.CLOSED -> current.copy(failureCount = 0)

                CircuitBreakerState.OPEN -> current
            }
        }
        return transition
    }

    override fun tryRecover(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            if (current.state == CircuitBreakerState.OPEN) {
                transition = CircuitBreakerTransition.HalfOpened
                current.copy(state = CircuitBreakerState.HALF_OPEN)
            } else {
                current
            }
        }
        return transition
    }
}
