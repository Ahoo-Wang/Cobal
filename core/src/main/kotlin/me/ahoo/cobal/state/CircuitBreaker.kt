package me.ahoo.cobal.state

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

interface AvailableCapable {
    val available: Boolean
}

enum class CircuitBreakerStatus {
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
    val status: CircuitBreakerStatus
    val recoverAt: Instant?

    fun onError(): CircuitBreakerTransition?
    fun onSuccess(): CircuitBreakerTransition?
    fun tryRecover(): CircuitBreakerTransition?
}

internal data class CircuitBreakerStat(
    val failureCount: Int = 0,
    val status: CircuitBreakerStatus = CircuitBreakerStatus.CLOSED,
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

    override val status: CircuitBreakerStatus
        get() = stat.get().status

    override val recoverAt: Instant?
        get() = stat.get().openedAt?.plus(recoveryDuration)

    override fun onError(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            when (current.status) {
                CircuitBreakerStatus.HALF_OPEN -> {
                    transition = CircuitBreakerTransition.ReHalfOpened
                    current.copy(
                        status = CircuitBreakerStatus.OPEN,
                        openedAt = Instant.now(),
                        failureCount = current.failureCount + 1
                    )
                }

                CircuitBreakerStatus.CLOSED -> {
                    val newCount = current.failureCount + 1
                    if (newCount >= threshold) {
                        transition = CircuitBreakerTransition.Opened
                        current.copy(
                            status = CircuitBreakerStatus.OPEN,
                            openedAt = Instant.now(),
                            failureCount = newCount
                        )
                    } else {
                        current.copy(failureCount = newCount)
                    }
                }

                CircuitBreakerStatus.OPEN -> current
            }
        }
        return transition
    }

    override fun onSuccess(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            when (current.status) {
                CircuitBreakerStatus.HALF_OPEN -> {
                    transition = CircuitBreakerTransition.Closed
                    CircuitBreakerStat.Default
                }

                CircuitBreakerStatus.CLOSED -> current.copy(failureCount = 0)

                CircuitBreakerStatus.OPEN -> current
            }
        }
        return transition
    }

    override fun tryRecover(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            if (current.status == CircuitBreakerStatus.OPEN) {
                transition = CircuitBreakerTransition.HalfOpened
                current.copy(status = CircuitBreakerStatus.HALF_OPEN)
            } else {
                current
            }
        }
        return transition
    }
}
