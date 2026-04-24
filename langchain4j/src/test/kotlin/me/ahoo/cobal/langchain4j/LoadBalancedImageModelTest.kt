package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.image.Image
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.output.Response
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.langchain4j.model.ImageModelNode
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LoadBalancedImageModelTest {
    @Test
    fun `should use load balancer to choose node`() {
        val mockModel = mockk<ImageModel>()
        every { mockModel.generate(any<String>()) } returns Response.from(mockk<Image>())

        val node = ImageModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbImage = LoadBalancedImageModel(lb, maxAttempts = 1)

        val result = lbImage.generate("a cat")
        result.assert().isNotNull()
        verify(exactly = 1) { mockModel.generate(any<String>()) }
    }

    @Test
    fun `should throw AllNodesUnavailableError when all nodes fail`() {
        val failingModel = mockk<ImageModel>()
        every { failingModel.generate(any<String>()) } throws RuntimeException("error")

        val node = ImageModelNode("node-1", model = failingModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbImage = LoadBalancedImageModel(lb, maxAttempts = 1)

        assertThrows<AllNodesUnavailableError> {
            lbImage.generate("a cat")
        }
    }
}
