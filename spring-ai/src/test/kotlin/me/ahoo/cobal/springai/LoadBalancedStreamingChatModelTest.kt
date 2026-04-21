package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.springai.model.StreamingChatModelNode
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class LoadBalancedStreamingChatModelTest {

    @Test
    fun `should stream from chosen node`() {
        val mockModel = mockk<StreamingChatModel>()
        val response = mockk<ChatResponse>()
        every { mockModel.stream(any<Prompt>()) } returns Flux.just(response)

        val node = StreamingChatModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedStreamingChatModel(lb, maxAttempts = 1)
        val prompt = mockk<Prompt>()

        StepVerifier.create(lbChat.stream(prompt))
            .expectNext(response)
            .verifyComplete()
    }

    @Test
    fun `should retry on stream error before emission`() {
        val failingModel = mockk<StreamingChatModel>()
        val successModel = mockk<StreamingChatModel>()
        every { failingModel.stream(any<Prompt>()) } returns Flux.error(RuntimeException("fail"))
        val response = mockk<ChatResponse>()
        every { successModel.stream(any<Prompt>()) } returns Flux.just(response)

        val failNode = StreamingChatModelNode("node-1", model = failingModel)
        val successNode = StreamingChatModelNode("node-2", model = successModel)
        val lb = RandomLoadBalancer(
            "lb",
            listOf(DefaultNodeState(failNode), DefaultNodeState(successNode))
        )
        val lbChat = LoadBalancedStreamingChatModel(lb, maxAttempts = 2)

        StepVerifier.create(lbChat.stream(mockk<Prompt>()))
            .expectNext(response)
            .verifyComplete()
    }

    @Test
    fun `should not retry if emission already started`() {
        val mockModel = mockk<StreamingChatModel>()
        val response = mockk<ChatResponse>()
        every { mockModel.stream(any<Prompt>()) } returns Flux.just(response)
            .concatWith(Flux.error(RuntimeException("mid-stream fail")))

        val node = StreamingChatModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedStreamingChatModel(lb, maxAttempts = 1)

        StepVerifier.create(lbChat.stream(mockk<Prompt>()))
            .expectNext(response)
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `should error with AllNodesUnavailableError when all nodes fail`() {
        val failingModel = mockk<StreamingChatModel>()
        every { failingModel.stream(any<Prompt>()) } returns Flux.error(RuntimeException("fail"))

        val node = StreamingChatModelNode("node-1", model = failingModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedStreamingChatModel(lb, maxAttempts = 1)

        StepVerifier.create(lbChat.stream(mockk<Prompt>()))
            .expectError(AllNodesUnavailableError::class.java)
            .verify()
    }
}
