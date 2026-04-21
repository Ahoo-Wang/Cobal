package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import io.mockk.every
import io.mockk.mockk
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.langchain4j.model.EmbeddingModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancedEmbeddingModelTest {
    @Test
    fun `should compile and extend EmbeddingModel`() {
        val mockModel = mockk<EmbeddingModel>()
        every { mockModel.embedAll(any<List<TextSegment>>()) } returns Response.from(listOf(Embedding.from(listOf(0.1f, 0.2f))))

        val node = EmbeddingModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbEmbedding = LoadBalancedEmbeddingModel(lb, maxRetries = 1)
        lbEmbedding.assert().isNotNull()
    }
}
