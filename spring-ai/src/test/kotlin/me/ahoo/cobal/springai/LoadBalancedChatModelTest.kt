package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.springai.model.ChatModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt

class LoadBalancedChatModelTest {
    @Test
    fun `should use load balancer to choose node`() {
        val mockModel = mockk<ChatModel>()
        val response = mockk<ChatResponse>()
        every { mockModel.call(any<Prompt>()) } returns response

        val node = ChatModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedChatModel(lb, maxRetries = 1)
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
        val lbChat = LoadBalancedChatModel(lb, maxRetries = 1)
        val prompt = mockk<Prompt>()

        assertThrows<AllNodesUnavailableError> {
            lbChat.call(prompt)
        }
    }
}
