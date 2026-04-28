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
import org.springframework.ai.audio.tts.TextToSpeechModel
import org.springframework.ai.audio.tts.TextToSpeechPrompt
import org.springframework.ai.audio.tts.TextToSpeechResponse
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration

class LoadBalancedTextToSpeechModelTest {

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
        val model = mockk<TextToSpeechModel>()
        val prompt = mockk<TextToSpeechPrompt>()
        val expectedResponse = mockk<TextToSpeechResponse>()

        every { model.call(any<TextToSpeechPrompt>()) } returns expectedResponse

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedTextToSpeechModel(lb)

        val result = balancedModel.call(prompt)
        result.assert().isEqualTo(expectedResponse)
        verify { model.call(any<TextToSpeechPrompt>()) }
    }

    @Test
    fun `call should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<TextToSpeechModel>()
        val prompt = mockk<TextToSpeechPrompt>()

        every { model.call(any<TextToSpeechPrompt>()) } throws RuntimeException("fail")

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedTextToSpeechModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.call(prompt)
        }
    }

    @Test
    fun `stream should return flux from chosen node`() {
        val model = mockk<TextToSpeechModel>()
        val response = mockk<TextToSpeechResponse>()
        every { model.stream(any<TextToSpeechPrompt>()) } returns Flux.just(response)

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedTextToSpeechModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<TextToSpeechPrompt>()))
            .expectNext(response)
            .verifyComplete()
    }

    @Test
    fun `stream should retry on error before emission`() {
        val model1 = mockk<TextToSpeechModel>()
        val model2 = mockk<TextToSpeechModel>()
        every { model1.stream(any<TextToSpeechPrompt>()) } returns Flux.error(RuntimeException("fail"))
        val response = mockk<TextToSpeechResponse>()
        every { model2.stream(any<TextToSpeechPrompt>()) } returns Flux.just(response)

        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1))
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RandomLoadBalancer("test-lb", listOf(state1, state2))
        val balancedModel = LoadBalancedTextToSpeechModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<TextToSpeechPrompt>()))
            .expectNext(response)
            .verifyComplete()
    }

    @Test
    fun `stream should not retry after emission already started`() {
        val model = mockk<TextToSpeechModel>()
        val response = mockk<TextToSpeechResponse>()
        every { model.stream(any<TextToSpeechPrompt>()) } returns Flux.just(response)
            .concatWith(Flux.error(RuntimeException("mid-stream fail")))

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedTextToSpeechModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<TextToSpeechPrompt>()))
            .expectNext(response)
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `stream should error with AllNodesUnavailableError when all nodes fail`() {
        val model = mockk<TextToSpeechModel>()
        every { model.stream(any<TextToSpeechPrompt>()) } returns Flux.error(RuntimeException("fail"))

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedTextToSpeechModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<TextToSpeechPrompt>()))
            .expectError(AllNodesUnavailableError::class.java)
            .verify()
    }

    @Test
    fun `stream should skip node when circuit breaker denies permission`() {
        val model1 = mockk<TextToSpeechModel>()
        val model2 = mockk<TextToSpeechModel>()
        val response = mockk<TextToSpeechResponse>()
        every { model2.stream(any<TextToSpeechPrompt>()) } returns Flux.just(response)

        val cb1 = CircuitBreaker.of("node-1", strictCircuitBreakerConfig())
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        cb1.state.assert().isEqualTo(CircuitBreaker.State.OPEN)

        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1), cb1)
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state1, state2))
        val balancedModel = LoadBalancedTextToSpeechModel(lb)

        StepVerifier.create(balancedModel.stream(mockk<TextToSpeechPrompt>()))
            .expectNext(response)
            .verifyComplete()

        verify(exactly = 0) { model1.stream(any<TextToSpeechPrompt>()) }
    }
}
