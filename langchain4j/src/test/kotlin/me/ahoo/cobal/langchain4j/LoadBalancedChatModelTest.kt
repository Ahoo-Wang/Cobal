package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.langchain4j.model.ChatModelNode
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LoadBalancedChatModelTest {
    @Test
    fun `should use load balancer to choose node`() {
        val mockModel = mockk<ChatModel>()
        val response = ChatResponse.builder()
            .aiMessage(AiMessage.from("OK"))
            .build()
        every { mockModel.chat(any<ChatRequest>()) } returns response

        val node = ChatModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedChatModel(lb, maxAttempts = 1)
        val request = ChatRequest.builder()
            .messages(listOf(UserMessage.from("Hi")))
            .build()
        val chatResponse = lbChat.chat(request)

        chatResponse.assert().isNotNull()
        verify(exactly = 1) { mockModel.chat(any<ChatRequest>()) }
    }

    @Test
    fun `should throw AllNodesUnavailableError when all nodes fail`() {
        val failingModel = mockk<ChatModel>()
        every { failingModel.chat(any<ChatRequest>()) } throws RuntimeException("error")

        val node = ChatModelNode("node-1", model = failingModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedChatModel(lb, maxAttempts = 1)
        val request = ChatRequest.builder()
            .messages(listOf(UserMessage.from("Hi")))
            .build()

        assertThrows<AllNodesUnavailableError> {
            lbChat.chat(request)
        }
    }
}
