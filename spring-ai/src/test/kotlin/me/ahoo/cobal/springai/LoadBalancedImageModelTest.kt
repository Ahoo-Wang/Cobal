package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.springai.model.ImageModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.ai.image.ImageModel
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.image.ImageResponse

class LoadBalancedImageModelTest {
    @Test
    fun `should use load balancer for image generation`() {
        val mockModel = mockk<ImageModel>()
        val response = mockk<ImageResponse>()
        every { mockModel.call(any<ImagePrompt>()) } returns response

        val node = ImageModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbImage = LoadBalancedImageModel(lb, maxRetries = 1)

        val result = lbImage.call(mockk())
        result.assert().isNotNull()
    }
}
