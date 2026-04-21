package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.StreamingChatModel
import io.mockk.every
import io.mockk.mockk
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.langchain4j.model.StreamingChatModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancedStreamingChatModelTest {
    @Test
    fun `should compile and extend StreamingChatModel`() {
        val mockModel = mockk<StreamingChatModel>()
        every { mockModel.chat(any<String>(), any()) } returns Unit

        val node = StreamingChatModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbChat = LoadBalancedStreamingChatModel(lb, maxRetries = 1)
        lbChat.assert().isNotNull()
    }
}
