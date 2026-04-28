package me.ahoo.cobal.springai

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.algorithm.RoundRobinLoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration

class LoadBalancedChatModelTest {

    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `call should delegate to underlying model`() {
        val model = mockk<ChatModel>()
        val prompt = mockk<Prompt>()
        val expectedResponse = mockk<ChatResponse>()

        every { model.call(any<Prompt>()) } returns expectedResponse

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedChatModel(lb)

        val result = balancedModel.call(prompt)
        result.assert().isEqualTo(expectedResponse)
        verify { model.call(any<Prompt>()) }
    }

    @Test
    fun `call should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<ChatModel>()
        val prompt = mockk<Prompt>()

        every { model.call(any<Prompt>()) } throws RuntimeException("fail")

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedChatModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.call(prompt)
        }
    }

    @Test
    fun `stream should return flux from chosen node`() {
        val model = mockk<ChatModel>()
        val response = mockk<ChatResponse>()
        every { model.stream(any<Prompt>()) } returns Flux.just(response)

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedChatModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<Prompt>()))
            .expectNext(response)
            .verifyComplete()
    }

    @Test
    fun `stream should retry on error before emission`() {
        val model1 = mockk<ChatModel>()
        val model2 = mockk<ChatModel>()
        every { model1.stream(any<Prompt>()) } returns Flux.error(RuntimeException("fail"))
        val response = mockk<ChatResponse>()
        every { model2.stream(any<Prompt>()) } returns Flux.just(response)

        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1))
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RandomLoadBalancer("test-lb", listOf(state1, state2))
        val balancedModel = LoadBalancedChatModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<Prompt>()))
            .expectNext(response)
            .verifyComplete()
    }

    @Test
    fun `stream should not retry after emission already started`() {
        val model = mockk<ChatModel>()
        val response = mockk<ChatResponse>()
        every { model.stream(any<Prompt>()) } returns Flux.just(response)
            .concatWith(Flux.error(RuntimeException("mid-stream fail")))

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedChatModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<Prompt>()))
            .expectNext(response)
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `stream should error with AllNodesUnavailableError when all nodes fail`() {
        val model = mockk<ChatModel>()
        every { model.stream(any<Prompt>()) } returns Flux.error(RuntimeException("fail"))

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedChatModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<Prompt>()))
            .expectError(AllNodesUnavailableError::class.java)
            .verify()
    }

    @Test
    fun `stream should skip node when circuit breaker denies permission`() {
        val model1 = mockk<ChatModel>()
        val model2 = mockk<ChatModel>()
        val response = mockk<ChatResponse>()
        every { model2.stream(any<Prompt>()) } returns Flux.just(response)

        val cb1 = CircuitBreaker.of("node-1", strictCircuitBreakerConfig())
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        cb1.state.assert().isEqualTo(CircuitBreaker.State.OPEN)

        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1), cb1)
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state1, state2))
        val balancedModel = LoadBalancedChatModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<Prompt>()))
            .expectNext(response)
            .verifyComplete()

        verify(exactly = 0) { model1.stream(any<Prompt>()) }
    }
}
