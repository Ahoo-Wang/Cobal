package me.ahoo.cobal

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.AuthenticationError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.error.NetworkError
import me.ahoo.cobal.error.NodeError
import me.ahoo.cobal.error.NodeErrorConverter
import me.ahoo.cobal.error.RateLimitError
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertFailsWith

@Suppress("TooGenericExceptionThrown")
class LoadBalancerExecuteTest {

    @Test
    fun `execute should return result on successful call`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
        }

        val result = lb.execute(NodeErrorConverter.Default) { model ->
            "$model-result"
        }

        result.assert().isEqualTo("model-1-result")
    }

    @Test
    fun `execute should retry on retriable error and succeed`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") {
                model("model-1")
                circuitBreaker {
                    failureRateThreshold(100.0f)
                    slidingWindowSize(1)
                    minimumNumberOfCalls(1)
                    waitDurationInOpenState(Duration.ofSeconds(60))
                }
            }
            node("node-2") { model("model-2") }
        }

        var callCount = 0
        val converter = NodeErrorConverter { nodeId, _ ->
            RateLimitError(nodeId, null)
        }

        val result = lb.execute(converter) { model ->
            callCount++
            if (callCount == 1) throw RuntimeException("rate limited")
            "$model-success"
        }

        callCount.assert().isEqualTo(2)
        result.assert().isEqualTo("model-2-success")
    }

    @Test
    fun `execute should throw InvalidRequestError immediately without retry`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
            node("node-2") { model("model-2") }
        }

        var callCount = 0
        val converter = NodeErrorConverter { nodeId, _ ->
            InvalidRequestError(nodeId, null)
        }

        assertThrownBy<InvalidRequestError> {
            lb.execute(converter) { model ->
                callCount++
                throw RuntimeException("bad request")
            }
        }

        callCount.assert().isEqualTo(1)
    }

    @Test
    fun `execute should throw AuthenticationError immediately without retry`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
            node("node-2") { model("model-2") }
        }

        var callCount = 0
        val converter = NodeErrorConverter { nodeId, _ ->
            AuthenticationError(nodeId, null)
        }

        assertThrownBy<AuthenticationError> {
            lb.execute(converter) { _ ->
                callCount++
                throw RuntimeException("auth failed")
            }
        }

        callCount.assert().isEqualTo(1)
        lb.states[0].circuitBreaker.metrics.numberOfFailedCalls.assert().isEqualTo(0)
        lb.states[0].circuitBreaker.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `execute should throw AllNodesUnavailableError when all retries exhausted`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
            node("node-2") { model("model-2") }
        }

        val converter = NodeErrorConverter { nodeId, _ ->
            NetworkError(nodeId, null)
        }

        assertThrownBy<AllNodesUnavailableError> {
            lb.execute(converter) { _ ->
                throw RuntimeException("fail")
            }
        }
    }

    @Test
    fun `execute should skip node when circuit breaker denies permission`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") {
                model("model-1")
                circuitBreaker {
                    failureRateThreshold(100.0f)
                    slidingWindowSize(1)
                    minimumNumberOfCalls(1)
                    waitDurationInOpenState(Duration.ofSeconds(60))
                }
            }
            node("node-2") { model("model-2") }
        }

        // Trip node-1's circuit breaker so tryAcquirePermission returns false
        val cb1 = lb.states[0].circuitBreaker
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        cb1.state.assert().isEqualTo(CircuitBreaker.State.OPEN)

        val calledModels = mutableListOf<String>()
        lb.execute(NodeErrorConverter.Default) { model ->
            calledModels.add(model)
            "result"
        }

        calledModels.assert().containsExactly("model-2")
    }

    @Test
    fun `execute should record success on circuit breaker`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
        }

        lb.execute(NodeErrorConverter.Default) { "result" }

        val cb = lb.states[0].circuitBreaker
        cb.metrics.numberOfSuccessfulCalls.assert().isEqualTo(1)
        cb.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `execute should record error on circuit breaker`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
            node("node-2") { model("model-2") }
        }

        val converter = NodeErrorConverter { nodeId, _ ->
            NetworkError(nodeId, null)
        }

        var callCount = 0
        lb.execute(converter) { _ ->
            callCount++
            if (callCount == 1) throw RuntimeException("fail")
            "result"
        }

        val cb1 = lb.states[0].circuitBreaker
        cb1.metrics.numberOfFailedCalls.assert().isEqualTo(1)
    }

    @Test
    fun `execute should use custom maxAttempts`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
        }

        var callCount = 0
        val converter = NodeErrorConverter { nodeId, _ ->
            NetworkError(nodeId, null)
        }

        assertThrownBy<AllNodesUnavailableError> {
            lb.execute(converter, maxAttempts = 3) { _ ->
                callCount++
                throw RuntimeException("fail")
            }
        }

        callCount.assert().isEqualTo(3)
    }

    @Test
    fun `execute should auto-size attempts when maxAttempts is zero`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
            node("node-2") { model("model-2") }
            node("node-3") { model("model-3") }
        }

        var callCount = 0
        val converter = NodeErrorConverter { nodeId, _ -> NetworkError(nodeId, null) }

        assertThrownBy<AllNodesUnavailableError> {
            lb.execute(converter, maxAttempts = 0) { _ ->
                callCount++
                throw RuntimeException("fail")
            }
        }

        // Auto-sized to availableStates.size (3 nodes)
        callCount.assert().isEqualTo(3)
    }

    @Test
    fun `execute should reject negative maxAttempts`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
        }

        assertThrownBy<IllegalArgumentException> {
            lb.execute(NodeErrorConverter.Default, maxAttempts = -1) { "result" }
        }
    }

    @Test
    fun `execute should attach all node failures as suppressed in AllNodesUnavailableError`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") { model("model-1") }
            node("node-2") { model("model-2") }
        }

        val converter = NodeErrorConverter { nodeId, _ -> NetworkError(nodeId, null) }

        val error = assertFailsWith<AllNodesUnavailableError> {
            lb.execute(converter) { _ -> throw RuntimeException("fail") }
        }

        error.cause.assert().isInstanceOf(NodeError::class.java)
        // Two attempts → latest is `cause`, the earlier one is suppressed.
        error.suppressedExceptions.size.assert().isEqualTo(1)
        error.suppressedExceptions[0].assert().isInstanceOf(NodeError::class.java)
    }

    @Test
    fun `execute should fail fast when no nodes available at entry`() {
        val lb = loadBalancer<String>("test-lb") {
            roundRobin()
            node("node-1") {
                model("model-1")
                circuitBreaker {
                    failureRateThreshold(100.0f)
                    slidingWindowSize(1)
                    minimumNumberOfCalls(1)
                    waitDurationInOpenState(Duration.ofSeconds(60))
                }
            }
        }
        // Trip the only node so availableStates is empty before execute starts.
        val cb1 = lb.states[0].circuitBreaker
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        lb.availableStates.size.assert().isEqualTo(0)

        var callCount = 0
        assertThrownBy<AllNodesUnavailableError> {
            lb.execute(NodeErrorConverter.Default) { _ ->
                callCount++
                "result"
            }
        }

        callCount.assert().isEqualTo(0)
    }
}
