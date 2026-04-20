package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.image.Image
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.output.Response
import io.mockk.every
import io.mockk.mockk
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.langchain4j.model.ImageModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancedImageModelTest {
    @Test
    fun `should compile and extend ImageModel`() {
        val mockModel = mockk<ImageModel>()
        val image = Image.builder().url("http://example.com/image.png").build()
        every { mockModel.generate(any<String>()) } returns Response.from(image)

        val node = ImageModelNode("node-1", model = mockModel)
        val lb = RandomLoadBalancer("lb", listOf(node))
        val lbImage = LoadBalancedImageModel(lb, maxRetries = 1)
        lbImage.assert().isNotNull()
    }
}
