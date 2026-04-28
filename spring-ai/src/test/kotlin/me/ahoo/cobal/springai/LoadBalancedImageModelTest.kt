package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.image.ImageModel
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.image.ImageResponse

class LoadBalancedImageModelTest {

    @Test
    fun `call should delegate to underlying model`() {
        val model = mockk<ImageModel>()
        val prompt = mockk<ImagePrompt>()
        val expectedResponse = mockk<ImageResponse>()

        every { model.call(any<ImagePrompt>()) } returns expectedResponse

        val lb = loadBalancer<ImageModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedImageModel(lb)

        val result = balancedModel.call(prompt)
        result.assert().isEqualTo(expectedResponse)
        verify { model.call(any<ImagePrompt>()) }
    }

    @Test
    fun `call should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<ImageModel>()
        val prompt = mockk<ImagePrompt>()

        every { model.call(any<ImagePrompt>()) } throws RuntimeException("fail")

        val lb = loadBalancer<ImageModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedImageModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.call(prompt)
        }
    }
}
