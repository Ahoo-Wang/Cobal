package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

class LoadBalancedEmbeddingModelTest {

    @Test
    fun `call should delegate to underlying model`() {
        val model = mockk<EmbeddingModel>()
        val request = mockk<EmbeddingRequest>()
        val expectedResponse = mockk<EmbeddingResponse>()

        every { model.call(any<EmbeddingRequest>()) } returns expectedResponse

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedEmbeddingModel(lb)

        val result = balancedModel.call(request)
        result.assert().isEqualTo(expectedResponse)
        verify { model.call(any<EmbeddingRequest>()) }
    }

    @Test
    fun `embed should delegate to underlying model`() {
        val model = mockk<EmbeddingModel>()
        val expected = floatArrayOf(0.1f, 0.2f)

        every { model.embed(any<Document>()) } returns expected

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedEmbeddingModel(lb)

        val result = balancedModel.embed(Document("test"))
        result.assert().isEqualTo(expected)
        verify { model.embed(any<Document>()) }
    }

    @Test
    fun `call should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<EmbeddingModel>()
        val request = mockk<EmbeddingRequest>()

        every { model.call(any<EmbeddingRequest>()) } throws RuntimeException("fail")

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedEmbeddingModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.call(request)
        }
    }
}
