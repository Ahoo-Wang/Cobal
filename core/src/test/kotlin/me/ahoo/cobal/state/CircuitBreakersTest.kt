package me.ahoo.cobal.state

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.DefaultNode
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.error.NetworkError
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class CircuitBreakersTest {

    @Test
    fun `defaultCircuitBreaker should create circuit breaker with correct name`() {
        val cb = defaultCircuitBreaker("node-1")
        cb.name.assert().isEqualTo("node-1")
    }

    @Test
    fun `DEFAULT_CIRCUIT_BREAKER_CONFIG should have count-based sliding window of 5`() {
        val config = DEFAULT_CIRCUIT_BREAKER_CONFIG
        config.slidingWindowSize.assert().isEqualTo(5)
        config.slidingWindowType.assert().isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
    }

    @Test
    fun `DEFAULT_CIRCUIT_BREAKER_CONFIG should have 100 percent failure rate threshold`() {
        DEFAULT_CIRCUIT_BREAKER_CONFIG.failureRateThreshold.assert().isEqualTo(100.0f)
    }

    @Test
    fun `DEFAULT_CIRCUIT_BREAKER_CONFIG should ignore InvalidRequestError`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)
        val cb = state.circuitBreaker

        repeat(10) {
            cb.onError(0, cb.timestampUnit, InvalidRequestError("node-1", RuntimeException("bad request")))
        }

        cb.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `DEFAULT_CIRCUIT_BREAKER_CONFIG should open after 5 consecutive failures`() {
        val cb = defaultCircuitBreaker("node-1")

        repeat(5) {
            cb.onError(0, cb.timestampUnit, NetworkError("node-1", RuntimeException("fail")))
        }

        cb.state.assert().isEqualTo(CircuitBreaker.State.OPEN)
    }

    @Test
    fun `DEFAULT_CIRCUIT_BREAKER_CONFIG should have minimum number of calls equal to sliding window`() {
        DEFAULT_CIRCUIT_BREAKER_CONFIG.minimumNumberOfCalls.assert().isEqualTo(5)
    }

    @Test
    fun `DEFAULT_CIRCUIT_BREAKER_CONFIG should not open before 5 failures`() {
        val cb = defaultCircuitBreaker("node-1")

        repeat(4) {
            cb.onError(0, cb.timestampUnit, NetworkError("node-1", RuntimeException("fail")))
        }

        cb.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
    }
}
