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
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse
import org.springframework.ai.audio.transcription.TranscriptionModel

class LoadBalancedAudioTranscriptionModelTest {

    @Test
    fun `call should delegate to underlying model`() {
        val model = mockk<TranscriptionModel>()
        val prompt = mockk<AudioTranscriptionPrompt>()
        val expectedResponse = mockk<AudioTranscriptionResponse>()

        every { model.call(any<AudioTranscriptionPrompt>()) } returns expectedResponse

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedAudioTranscriptionModel(lb)

        val result = balancedModel.call(prompt)
        result.assert().isEqualTo(expectedResponse)
        verify { model.call(any<AudioTranscriptionPrompt>()) }
    }

    @Test
    fun `call should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<TranscriptionModel>()
        val prompt = mockk<AudioTranscriptionPrompt>()

        every { model.call(any<AudioTranscriptionPrompt>()) } throws RuntimeException("fail")

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedAudioTranscriptionModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.call(prompt)
        }
    }
}
