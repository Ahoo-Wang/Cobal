package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.algorithm.RoundRobinLoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration

class LoadBalancedStreamingChatModelTest {

    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `chat should stream response successfully on first attempt`() {
        val model = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val response = mockk<ChatResponse>()
        val handlerSlot = slot<StreamingChatResponseHandler>()

        every { model.chat(any<ChatRequest>(), capture(handlerSlot)) } answers { }

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)
        handlerSlot.captured.onCompleteResponse(response)

        verify { delegate.onCompleteResponse(response) }
    }

    @Test
    fun `chat should retry on retriable error and succeed on second node`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val response = mockk<ChatResponse>()
        val handler1Slot = slot<StreamingChatResponseHandler>()
        val handler2Slot = slot<StreamingChatResponseHandler>()

        every { model1.chat(any<ChatRequest>(), capture(handler1Slot)) } answers { }
        every { model2.chat(any<ChatRequest>(), capture(handler2Slot)) } answers { }

        val cb1 = CircuitBreaker.of("node-1", strictCircuitBreakerConfig())
        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1), cb1)
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state1, state2))
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        // node-1 (RoundRobin first) fails
        handler1Slot.captured.onError(RuntimeException("rate limited"))

        // node-2 succeeds
        handler2Slot.captured.onCompleteResponse(response)

        verify { delegate.onCompleteResponse(response) }
        verify(exactly = 0) { delegate.onError(any()) }
    }

    @Test
    fun `chat should propagate InvalidRequestError immediately without retry`() {
        val model = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val handlerSlot = slot<StreamingChatResponseHandler>()

        every { model.chat(any<ChatRequest>(), capture(handlerSlot)) } answers { }

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        // Trigger InvalidRequestException -> InvalidRequestError
        handlerSlot.captured.onError(dev.langchain4j.exception.InvalidRequestException("bad request"))

        verify { delegate.onError(match { it is InvalidRequestError }) }
    }

    @Test
    fun `chat should notify delegate with AllNodesUnavailableError when all retries exhausted`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val handlerSlot = mutableListOf<StreamingChatResponseHandler>()

        every { model1.chat(any<ChatRequest>(), capture(handlerSlot)) } answers { }
        every { model2.chat(any<ChatRequest>(), capture(handlerSlot)) } answers { }

        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1))
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state1, state2))
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        handlerSlot[0].onError(RuntimeException("fail"))
        handlerSlot[1].onError(RuntimeException("fail again"))

        verify { delegate.onError(match { it is AllNodesUnavailableError }) }
    }

    @Test
    fun `chat should skip node when circuit breaker denies permission`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val response = mockk<ChatResponse>()
        val handler2Slot = slot<StreamingChatResponseHandler>()

        every { model2.chat(any<ChatRequest>(), capture(handler2Slot)) } answers { }

        val cb1 = CircuitBreaker.of("node-1", strictCircuitBreakerConfig())
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        cb1.state.assert().isEqualTo(CircuitBreaker.State.OPEN)

        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1), cb1)
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state1, state2))
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        verify(exactly = 0) { model1.chat(any<ChatRequest>(), any()) }
        handler2Slot.captured.onCompleteResponse(response)

        verify { delegate.onCompleteResponse(response) }
    }

    @Test
    fun `chat should not notify delegate onError when retrying succeeds`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val response = mockk<ChatResponse>()
        val handler1Slot = slot<StreamingChatResponseHandler>()
        val handler2Slot = slot<StreamingChatResponseHandler>()

        every { model1.chat(any<ChatRequest>(), capture(handler1Slot)) } answers { }
        every { model2.chat(any<ChatRequest>(), capture(handler2Slot)) } answers { }

        val cb1 = CircuitBreaker.of("node-1", strictCircuitBreakerConfig())
        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1), cb1)
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state1, state2))
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        // node-1 fails with retriable error
        handler1Slot.captured.onError(RuntimeException("rate limited"))

        // node-2 succeeds
        handler2Slot.captured.onCompleteResponse(response)

        verify(exactly = 0) { delegate.onError(any()) }
        verify(exactly = 1) { delegate.onCompleteResponse(response) }
    }

    @Test
    fun `chat should use current available states size for default maxAttempts`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val handler2Slot = slot<StreamingChatResponseHandler>()

        every { model2.chat(any<ChatRequest>(), capture(handler2Slot)) } answers { }

        val cb1 = CircuitBreaker.of("node-1", strictCircuitBreakerConfig())
        val state1 = DefaultNodeState(DefaultModelNode("node-1", model = model1), cb1)
        val state2 = DefaultNodeState(DefaultModelNode("node-2", model = model2))
        val lb = RoundRobinLoadBalancer("test-lb", listOf(state1, state2))

        lb.availableStates.size.assert().isEqualTo(2)
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        // Trip CB to reduce available states to 1
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        lb.availableStates.size.assert().isEqualTo(1)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        // node-1 skipped (CB open), only node-2 used
        verify(exactly = 0) { model1.chat(any<ChatRequest>(), any()) }
        verify(exactly = 1) { model2.chat(any<ChatRequest>(), any()) }
    }
}
