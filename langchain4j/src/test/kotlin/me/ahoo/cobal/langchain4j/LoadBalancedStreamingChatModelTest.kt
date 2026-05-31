package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.AuthenticationError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration

class LoadBalancedStreamingChatModelTest {

    @Test
    fun `chat should stream response successfully on first attempt`() {
        val model = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val response = mockk<ChatResponse>()
        val handlerSlot = slot<StreamingChatResponseHandler>()

        every { model.chat(any<ChatRequest>(), capture(handlerSlot)) } answers { }

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") { model(model) }
        }
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

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") {
                model(model1)
                circuitBreaker {
                    failureRateThreshold(100.0f)
                    slidingWindowSize(1)
                    minimumNumberOfCalls(1)
                    waitDurationInOpenState(Duration.ofSeconds(60))
                }
            }
            node("node-2") { model(model2) }
        }
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

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        // Trigger InvalidRequestException -> InvalidRequestError
        handlerSlot.captured.onError(dev.langchain4j.exception.InvalidRequestException("bad request"))

        verify { delegate.onError(match { it is InvalidRequestError }) }
    }

    @Test
    fun `chat should propagate AuthenticationError immediately without retry`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>(relaxed = true)
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val handlerSlot = slot<StreamingChatResponseHandler>()

        every { model1.chat(any<ChatRequest>(), capture(handlerSlot)) } answers { }

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") { model(model1) }
            node("node-2") { model(model2) }
        }
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        handlerSlot.captured.onError(dev.langchain4j.exception.AuthenticationException("auth failed"))

        verify { delegate.onError(match { it is AuthenticationError }) }
        verify(exactly = 0) { model2.chat(any<ChatRequest>(), any()) }
        lb.states[0].circuitBreaker.metrics.numberOfFailedCalls.assert().isEqualTo(0)
        lb.states[0].circuitBreaker.state.assert().isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `chat should notify delegate with AllNodesUnavailableError when all retries exhausted`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val handlerSlot = mutableListOf<StreamingChatResponseHandler>()

        every { model1.chat(any<ChatRequest>(), capture(handlerSlot)) } answers { }
        every { model2.chat(any<ChatRequest>(), capture(handlerSlot)) } answers { }

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") { model(model1) }
            node("node-2") { model(model2) }
        }
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

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") {
                model(model1)
                circuitBreaker {
                    failureRateThreshold(100.0f)
                    slidingWindowSize(1)
                    minimumNumberOfCalls(1)
                    waitDurationInOpenState(Duration.ofSeconds(60))
                }
            }
            node("node-2") { model(model2) }
        }

        val cb1 = lb.states[0].circuitBreaker
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        cb1.state.assert().isEqualTo(CircuitBreaker.State.OPEN)

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

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") {
                model(model1)
                circuitBreaker {
                    failureRateThreshold(100.0f)
                    slidingWindowSize(1)
                    minimumNumberOfCalls(1)
                    waitDurationInOpenState(Duration.ofSeconds(60))
                }
            }
            node("node-2") { model(model2) }
        }
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
    fun `chat should drive retry loop without recursion when onError is invoked synchronously`() {
        // Synchronous-callback provider: model.chat(...) invokes handler.onError(...) before returning.
        // This exercises the `processing=true` branch — retry must drain via the outer while loop,
        // not via a recursive runLoop() call.
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val response = mockk<ChatResponse>()
        val handler2Slot = slot<StreamingChatResponseHandler>()

        every { model1.chat(any<ChatRequest>(), any()) } answers {
            secondArg<StreamingChatResponseHandler>().onError(RuntimeException("rate limited"))
        }
        every { model2.chat(any<ChatRequest>(), capture(handler2Slot)) } answers { }

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") { model(model1) }
            node("node-2") { model(model2) }
        }
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        balancedModel.chat(
            ChatRequest.builder().messages(UserMessage.from("hello")).build(),
            delegate,
        )

        // node-2 was selected synchronously after node-1's onError — assert by completing it.
        handler2Slot.captured.onCompleteResponse(response)
        verify { delegate.onCompleteResponse(response) }
        verify(exactly = 0) { delegate.onError(any()) }
    }

    @Test
    fun `chat should retry when model_chat throws retriable exception synchronously`() {
        // Exercises the synchronous-exception catch block in step() with a retriable failure.
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val response = mockk<ChatResponse>()
        val handler2Slot = slot<StreamingChatResponseHandler>()

        every { model1.chat(any<ChatRequest>(), any()) } throws RuntimeException("transient")
        every { model2.chat(any<ChatRequest>(), capture(handler2Slot)) } answers { }

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") { model(model1) }
            node("node-2") { model(model2) }
        }
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        balancedModel.chat(
            ChatRequest.builder().messages(UserMessage.from("hello")).build(),
            delegate,
        )

        handler2Slot.captured.onCompleteResponse(response)
        verify { delegate.onCompleteResponse(response) }
        verify(exactly = 0) { delegate.onError(any()) }
    }

    @Test
    fun `chat should terminate immediately when model_chat throws InvalidRequestException synchronously`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>(relaxed = true)
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)

        every {
            model1.chat(any<ChatRequest>(), any())
        } throws dev.langchain4j.exception.InvalidRequestException("bad request")

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") { model(model1) }
            node("node-2") { model(model2) }
        }
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        balancedModel.chat(
            ChatRequest.builder().messages(UserMessage.from("hello")).build(),
            delegate,
        )

        verify { delegate.onError(match { it is InvalidRequestError }) }
        // node-2 must not be tried — InvalidRequestError short-circuits.
        verify(exactly = 0) { model2.chat(any<ChatRequest>(), any()) }
    }

    @Test
    fun `chat should fail fast when no nodes are available at entry`() {
        val model1 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") {
                model(model1)
                circuitBreaker {
                    failureRateThreshold(100.0f)
                    slidingWindowSize(1)
                    minimumNumberOfCalls(1)
                    waitDurationInOpenState(Duration.ofSeconds(60))
                }
            }
        }
        // Trip the only node before starting.
        val cb1 = lb.states[0].circuitBreaker
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        lb.availableStates.size.assert().isEqualTo(0)

        val balancedModel = LoadBalancedStreamingChatModel(lb)
        balancedModel.chat(
            ChatRequest.builder().messages(UserMessage.from("hello")).build(),
            delegate,
        )

        verify { delegate.onError(match { it is AllNodesUnavailableError }) }
        verify(exactly = 0) { model1.chat(any<ChatRequest>(), any()) }
    }

    @Test
    fun `chat should attach per-attempt failures as suppressed in AllNodesUnavailableError`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)

        every { model1.chat(any<ChatRequest>(), any()) } answers {
            secondArg<StreamingChatResponseHandler>().onError(RuntimeException("fail 1"))
        }
        every { model2.chat(any<ChatRequest>(), any()) } answers {
            secondArg<StreamingChatResponseHandler>().onError(RuntimeException("fail 2"))
        }

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") { model(model1) }
            node("node-2") { model(model2) }
        }
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        val errorSlot = slot<Throwable>()
        every { delegate.onError(capture(errorSlot)) } answers { }

        balancedModel.chat(
            ChatRequest.builder().messages(UserMessage.from("hello")).build(),
            delegate,
        )

        errorSlot.captured.assert().isInstanceOf(AllNodesUnavailableError::class.java)
        // Two attempts: latest is `cause`, earlier one is suppressed.
        errorSlot.captured.cause.assert().isNotNull()
        errorSlot.captured.suppressedExceptions.size.assert().isEqualTo(1)
    }

    @Test
    fun `chat should use current available states size for default maxAttempts`() {
        val model1 = mockk<StreamingChatModel>()
        val model2 = mockk<StreamingChatModel>()
        val delegate = mockk<StreamingChatResponseHandler>(relaxed = true)
        val handler2Slot = slot<StreamingChatResponseHandler>()

        every { model2.chat(any<ChatRequest>(), capture(handler2Slot)) } answers { }

        val lb = loadBalancer<StreamingChatModel>("test-lb") {
            roundRobin()
            node("node-1") {
                model(model1)
                circuitBreaker {
                    failureRateThreshold(100.0f)
                    slidingWindowSize(1)
                    minimumNumberOfCalls(1)
                    waitDurationInOpenState(Duration.ofSeconds(60))
                }
            }
            node("node-2") { model(model2) }
        }

        lb.availableStates.size.assert().isEqualTo(2)
        val balancedModel = LoadBalancedStreamingChatModel(lb)

        // Trip CB to reduce available states to 1
        val cb1 = lb.states[0].circuitBreaker
        cb1.onError(0, cb1.timestampUnit, RuntimeException("error"))
        lb.availableStates.size.assert().isEqualTo(1)

        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        balancedModel.chat(request, delegate)

        // node-1 skipped (CB open), only node-2 used
        verify(exactly = 0) { model1.chat(any<ChatRequest>(), any()) }
        verify(exactly = 1) { model2.chat(any<ChatRequest>(), any()) }
    }
}
