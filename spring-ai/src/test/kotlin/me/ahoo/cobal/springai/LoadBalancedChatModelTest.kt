package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.springai.model.ChatModelNode
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class LoadBalancedChatModelTest {
    @Test
    fun `should use load balancer to choose node`() {
        val mockModel = mockk<ChatModel>()
        val response = mockk<ChatResponse>()
        every { mockModel.call(any<Prompt>()) } returns response

        val node = ChatModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedChatModel(lb, maxAttempts = 1)
        val prompt = mockk<Prompt>()

        val chatResponse = lbChat.call(prompt)

        chatResponse.assert().isNotNull()
        verify(exactly = 1) { mockModel.call(any<Prompt>()) }
    }

    @Test
    fun `should throw AllNodesUnavailableError when all nodes fail`() {
        val failingModel = mockk<ChatModel>()
        every { failingModel.call(any<Prompt>()) } throws RuntimeException("error")

        val node = ChatModelNode("node-1", model = failingModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedChatModel(lb, maxAttempts = 1)
        val prompt = mockk<Prompt>()

        assertThrows<AllNodesUnavailableError> {
            lbChat.call(prompt)
        }
    }

    @Test
    fun `should stream from chosen node`() {
        val mockModel = mockk<ChatModel>()
        val response = mockk<ChatResponse>()
        every { mockModel.stream(any<Prompt>()) } returns Flux.just(response)

        val node = ChatModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedChatModel(lb, maxAttempts = 1)
        val prompt = mockk<Prompt>()

        StepVerifier.create(lbChat.stream(prompt))
            .expectNext(response)
            .verifyComplete()
    }

    @Test
    fun `should retry on stream error before emission`() {
        val failingModel = mockk<ChatModel>()
        val successModel = mockk<ChatModel>()
        every { failingModel.stream(any<Prompt>()) } returns Flux.error(RuntimeException("fail"))
        val response = mockk<ChatResponse>()
        every { successModel.stream(any<Prompt>()) } returns Flux.just(response)

        val failNode = ChatModelNode("node-1", model = failingModel)
        val successNode = ChatModelNode("node-2", model = successModel)
        val lb = RandomLoadBalancer(
            "lb",
            listOf(DefaultNodeState(failNode), DefaultNodeState(successNode))
        )
        val lbChat = LoadBalancedChatModel(lb, maxAttempts = 2)

        StepVerifier.create(lbChat.stream(mockk<Prompt>()))
            .expectNext(response)
            .verifyComplete()
    }

    @Test
    fun `should not retry if emission already started`() {
        val mockModel = mockk<ChatModel>()
        val response = mockk<ChatResponse>()
        every { mockModel.stream(any<Prompt>()) } returns Flux.just(response)
            .concatWith(Flux.error(RuntimeException("mid-stream fail")))

        val node = ChatModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedChatModel(lb, maxAttempts = 1)

        StepVerifier.create(lbChat.stream(mockk<Prompt>()))
            .expectNext(response)
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `should error with AllNodesUnavailableError when all nodes fail`() {
        val failingModel = mockk<ChatModel>()
        every { failingModel.stream(any<Prompt>()) } returns Flux.error(RuntimeException("fail"))

        val node = ChatModelNode("node-1", model = failingModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedChatModel(lb, maxAttempts = 1)

        StepVerifier.create(lbChat.stream(mockk<Prompt>()))
            .expectError(AllNodesUnavailableError::class.java)
            .verify()
    }
}
