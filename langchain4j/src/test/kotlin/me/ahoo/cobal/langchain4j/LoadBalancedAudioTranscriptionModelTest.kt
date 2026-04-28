package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.audio.AudioTranscriptionModel
import dev.langchain4j.model.audio.AudioTranscriptionRequest
import dev.langchain4j.model.audio.AudioTranscriptionResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class LoadBalancedAudioTranscriptionModelTest {

    @Test
    fun `transcribe should delegate to underlying model`() {
        val model = mockk<AudioTranscriptionModel>()
        val request = mockk<AudioTranscriptionRequest>()
        val expectedResponse = mockk<AudioTranscriptionResponse>()

        every { model.transcribe(any<AudioTranscriptionRequest>()) } returns expectedResponse

        val lb = loadBalancer<AudioTranscriptionModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedAudioTranscriptionModel(lb)

        val result = balancedModel.transcribe(request)
        result.assert().isEqualTo(expectedResponse)
        verify { model.transcribe(any<AudioTranscriptionRequest>()) }
    }

    @Test
    fun `transcribe should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<AudioTranscriptionModel>()
        val request = mockk<AudioTranscriptionRequest>()

        every { model.transcribe(any<AudioTranscriptionRequest>()) } throws RuntimeException("fail")

        val lb = loadBalancer<AudioTranscriptionModel>("test-lb") {
            random()
            node("node-1") { model(model) }
        }
        val balancedModel = LoadBalancedAudioTranscriptionModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.transcribe(request)
        }
    }
}
