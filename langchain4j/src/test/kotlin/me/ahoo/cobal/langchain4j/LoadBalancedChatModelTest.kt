package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class LoadBalancedChatModelTest {

    @Test
    fun `chat should delegate to underlying model`() {
        val model = mockk<ChatModel>()
        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()
        val expectedResponse = mockk<ChatResponse>()

        every { model.chat(any<ChatRequest>()) } returns expectedResponse

        val lb = loadBalancer<ChatModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedChatModel(lb)

        val result = balancedModel.chat(request)
        result.assert().isEqualTo(expectedResponse)
        verify { model.chat(any<ChatRequest>()) }
    }

    @Test
    fun `chat should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<ChatModel>()
        val request = ChatRequest.builder().messages(UserMessage.from("hello")).build()

        every { model.chat(any<ChatRequest>()) } throws RuntimeException("fail")

        val lb = loadBalancer<ChatModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedChatModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.chat(request)
        }
    }
}
