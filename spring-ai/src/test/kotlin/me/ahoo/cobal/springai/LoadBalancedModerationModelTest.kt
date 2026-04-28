package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.moderation.ModerationModel
import org.springframework.ai.moderation.ModerationPrompt
import org.springframework.ai.moderation.ModerationResponse

class LoadBalancedModerationModelTest {

    @Test
    fun `call should delegate to underlying model`() {
        val model = mockk<ModerationModel>()
        val prompt = mockk<ModerationPrompt>()
        val expectedResponse = mockk<ModerationResponse>()

        every { model.call(any<ModerationPrompt>()) } returns expectedResponse

        val lb = loadBalancer<ModerationModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedModerationModel(lb)

        val result = balancedModel.call(prompt)
        result.assert().isEqualTo(expectedResponse)
        verify { model.call(any<ModerationPrompt>()) }
    }

    @Test
    fun `call should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<ModerationModel>()
        val prompt = mockk<ModerationPrompt>()

        every { model.call(any<ModerationPrompt>()) } throws RuntimeException("fail")

        val lb = loadBalancer<ModerationModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedModerationModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.call(prompt)
        }
    }
}
