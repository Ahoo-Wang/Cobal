package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.chat.ChatModel
import io.mockk.mockk
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class ModelNodesTest {
    @Test
    fun `ChatModelNode should hold ChatModel`() {
        val mockModel = mockk<ChatModel>()
        val node = ChatModelNode("model-1", weight = 2, model = mockModel)
        node.id.assert().isEqualTo("model-1")
        node.weight.assert().isEqualTo(2)
        node.model.assert().isNotNull()
    }
}
