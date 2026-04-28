package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.DocumentEmbeddingModel
import org.springframework.ai.embedding.DocumentEmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

class LoadBalancedDocumentEmbeddingModelTest {

    @Test
    fun `call should delegate to underlying model`() {
        val model = mockk<DocumentEmbeddingModel>()
        val request = mockk<DocumentEmbeddingRequest>()
        val expectedResponse = mockk<EmbeddingResponse>()

        every { model.call(any<DocumentEmbeddingRequest>()) } returns expectedResponse

        val lb = loadBalancer<DocumentEmbeddingModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedDocumentEmbeddingModel(lb)

        val result = balancedModel.call(request)
        result.assert().isEqualTo(expectedResponse)
        verify { model.call(any<DocumentEmbeddingRequest>()) }
    }

    @Test
    fun `dimensions should delegate to underlying model`() {
        val model = mockk<DocumentEmbeddingModel>()
        every { model.dimensions() } returns 1536

        val lb = loadBalancer<DocumentEmbeddingModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedDocumentEmbeddingModel(lb)

        val result = balancedModel.dimensions()
        result.assert().isEqualTo(1536)
        verify { model.dimensions() }
    }

    @Test
    fun `call should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<DocumentEmbeddingModel>()
        val request = mockk<DocumentEmbeddingRequest>()

        every { model.call(any<DocumentEmbeddingRequest>()) } throws RuntimeException("fail")

        val lb = loadBalancer<DocumentEmbeddingModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedDocumentEmbeddingModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.call(request)
        }
    }
}
