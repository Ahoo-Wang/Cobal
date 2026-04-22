package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertTrue

class CircuitBreakerTest {

    @Test
    fun `DefaultCircuitBreaker should start CLOSED`() {
        val cb = DefaultCircuitBreaker()
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
        cb.recoverAt.assert().isNull()
    }

    @Test
    fun `onError should return null when below threshold`() {
        val cb = DefaultCircuitBreaker(threshold = 3)
        cb.onError().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
    }

    @Test
    fun `onError should return Opened at threshold`() {
        val cb = DefaultCircuitBreaker(threshold = 3)

        cb.onError().assert().isNull()
        cb.onError().assert().isNull()
        cb.onError().assert().isInstanceOf(CircuitBreakerTransition.Opened::class.java)

        cb.state.assert().isEqualTo(CircuitBreakerState.OPEN)
        cb.recoverAt.assert().isNotNull()
    }

    @Test
    fun `onError should return ReHalfOpened when HALF_OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 2)
        cb.onError()
        cb.onError()

        cb.tryRecover()
        cb.state.assert().isEqualTo(CircuitBreakerState.HALF_OPEN)

        cb.onError().assert().isInstanceOf(CircuitBreakerTransition.ReHalfOpened::class.java)
        cb.state.assert().isEqualTo(CircuitBreakerState.OPEN)
    }

    @Test
    fun `onError should return null when OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 1)
        cb.onError()

        cb.onError().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.OPEN)
    }

    @Test
    fun `onSuccess should return null and reset count when CLOSED`() {
        val cb = DefaultCircuitBreaker(threshold = 3)
        cb.onError()
        cb.onError()

        cb.onSuccess().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)

        // Count was reset — takes another 3 errors to open
        cb.onError().assert().isNull()
        cb.onError().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
    }

    @Test
    fun `onSuccess should return Closed when HALF_OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 1)
        cb.onError()
        cb.tryRecover()
        cb.state.assert().isEqualTo(CircuitBreakerState.HALF_OPEN)

        cb.onSuccess().assert().isInstanceOf(CircuitBreakerTransition.Closed::class.java)
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
        cb.recoverAt.assert().isNull()
    }

    @Test
    fun `tryRecover should return HalfOpened when OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 1, recoveryDuration = Duration.ofSeconds(30))
        cb.onError()

        val transition = cb.tryRecover()
        transition.assert().isInstanceOf(CircuitBreakerTransition.HalfOpened::class.java)
        cb.state.assert().isEqualTo(CircuitBreakerState.HALF_OPEN)
    }

    @Test
    fun `tryRecover should return null when CLOSED`() {
        val cb = DefaultCircuitBreaker()
        cb.tryRecover().assert().isNull()
    }

    @Test
    fun `tryRecover should return null when HALF_OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 1)
        cb.onError()
        cb.tryRecover()

        cb.tryRecover().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.HALF_OPEN)
    }

    @Test
    fun `recoverAt should be openedAt plus recoveryDuration`() {
        val before = Instant.now()
        val cb = DefaultCircuitBreaker(threshold = 1, recoveryDuration = Duration.ofSeconds(30))
        cb.onError()
        val after = Instant.now()

        val recoverAt = cb.recoverAt!!
        val expectedMin = before.plusSeconds(30)
        val expectedMax = after.plusSeconds(30)
        assertTrue(recoverAt >= expectedMin, "recoverAt should be >= $expectedMin but was $recoverAt")
        assertTrue(recoverAt <= expectedMax, "recoverAt should be <= $expectedMax but was $recoverAt")
    }

    @Test
    fun `recoverAt should be null when CLOSED`() {
        val cb = DefaultCircuitBreaker(threshold = 1, recoveryDuration = Duration.ofSeconds(30))
        cb.recoverAt.assert().isNull()
    }

    @Test
    fun `onSuccess resets count after HALF_OPEN to Closed cycle`() {
        val cb = DefaultCircuitBreaker(threshold = 2)
        cb.onError()
        cb.onError()
        cb.tryRecover()
        cb.onSuccess()

        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
        // Failure count is reset
        cb.onError().assert().isNull()
        cb.onError().assert().isInstanceOf(CircuitBreakerTransition.Opened::class.java)
    }

    @Test
    fun `ReHalfOpened should update openedAt`() {
        val cb = DefaultCircuitBreaker(threshold = 1, recoveryDuration = Duration.ofSeconds(10))
        cb.onError()
        val firstRecoverAt = cb.recoverAt!!

        cb.tryRecover()
        Thread.sleep(1)
        cb.onError()

        val secondRecoverAt = cb.recoverAt!!
        assertTrue(secondRecoverAt > firstRecoverAt, "secondRecoverAt should be > firstRecoverAt")
    }
}
