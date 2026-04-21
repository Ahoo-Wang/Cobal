package me.ahoo.cobal.springai

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.springai.model.AudioTranscriptionModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse
import org.springframework.ai.audio.transcription.TranscriptionModel

class LoadBalancedAudioTranscriptionModelTest {
    @Test
    fun `should use load balancer for audio transcription`() {
        val mockModel = mockk<TranscriptionModel>()
        val response = mockk<AudioTranscriptionResponse>()
        every { mockModel.call(any<AudioTranscriptionPrompt>()) } returns response

        val node = AudioTranscriptionModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbAudio = LoadBalancedAudioTranscriptionModel(lb, maxAttempts = 1)

        val result = lbAudio.call(mockk())
        result.assert().isNotNull()
    }
}
