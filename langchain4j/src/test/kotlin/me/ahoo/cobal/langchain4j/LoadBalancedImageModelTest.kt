package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.image.Image
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.output.Response
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

class LoadBalancedImageModelTest {

    @Test
    fun `generate should delegate to underlying model`() {
        val model = mockk<ImageModel>()
        val expectedResponse = mockk<Response<Image>>()

        every { model.generate(any<String>()) } returns expectedResponse

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedImageModel(lb)

        val result = balancedModel.generate("a cat")
        result.assert().isEqualTo(expectedResponse)
        verify { model.generate(any<String>()) }
    }

    @Test
    fun `generate should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<ImageModel>()

        every { model.generate(any<String>()) } throws RuntimeException("fail")

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedImageModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.generate("a cat")
        }
    }
}
