package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.langchain4j.model.EmbeddingModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LoadBalancedEmbeddingModelTest {
    @Test
    fun `should use load balancer to choose node`() {
        val mockModel = mockk<EmbeddingModel>()
        every { mockModel.embedAll(any<List<TextSegment>>()) } returns Response.from(
            listOf(Embedding.from(listOf(0.1f, 0.2f)))
        )

        val node = EmbeddingModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbEmbedding = LoadBalancedEmbeddingModel(lb, maxAttempts = 1)

        val result = lbEmbedding.embedAll(listOf(TextSegment.from("hello")))
        result.assert().isNotNull()
        verify(exactly = 1) { mockModel.embedAll(any<List<TextSegment>>()) }
    }

    @Test
    fun `should throw AllNodesUnavailableError when all nodes fail`() {
        val failingModel = mockk<EmbeddingModel>()
        every { failingModel.embedAll(any<List<TextSegment>>()) } throws RuntimeException("error")

        val node = EmbeddingModelNode("node-1", model = failingModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbEmbedding = LoadBalancedEmbeddingModel(lb, maxAttempts = 1)

        assertThrows<AllNodesUnavailableError> {
            lbEmbedding.embedAll(listOf(TextSegment.from("hello")))
        }
    }
}
