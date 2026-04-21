package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.springai.model.EmbeddingModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest

class LoadBalancedEmbeddingModelTest {
    @Test
    fun `should use load balancer for embedding`() {
        val mockModel = mockk<EmbeddingModel>()
        every { mockModel.embed(any<Document>()) } returns floatArrayOf(0.1f, 0.2f)
        every { mockModel.call(any<EmbeddingRequest>()) } returns mockk()

        val node = EmbeddingModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbEmbedding = LoadBalancedEmbeddingModel(lb, maxAttempts = 1)

        val result = lbEmbedding.embed(Document("test"))
        result.assert().isNotNull()
    }
}
