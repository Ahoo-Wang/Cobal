package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class LoadBalancedEmbeddingModelTest {

    @Test
    fun `embedAll should delegate to underlying model`() {
        val model = mockk<EmbeddingModel>()
        val segments = listOf(TextSegment.from("hello"))
        val expectedResponse = mockk<Response<List<Embedding>>>()

        every { model.embedAll(any<List<TextSegment>>()) } returns expectedResponse

        val lb = loadBalancer<EmbeddingModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedEmbeddingModel(lb)

        val result = balancedModel.embedAll(segments)
        result.assert().isEqualTo(expectedResponse)
        verify { model.embedAll(any<List<TextSegment>>()) }
    }

    @Test
    fun `embedAll should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<EmbeddingModel>()
        val segments = listOf(TextSegment.from("hello"))

        every { model.embedAll(any<List<TextSegment>>()) } throws RuntimeException("fail")

        val lb = loadBalancer<EmbeddingModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedEmbeddingModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.embedAll(segments)
        }
    }
}
