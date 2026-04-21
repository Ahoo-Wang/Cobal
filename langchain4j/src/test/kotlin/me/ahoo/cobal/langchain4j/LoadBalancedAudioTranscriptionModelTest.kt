package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.audio.AudioTranscriptionModel
import dev.langchain4j.model.audio.AudioTranscriptionRequest
import dev.langchain4j.model.audio.AudioTranscriptionResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.langchain4j.model.AudioTranscriptionModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LoadBalancedAudioTranscriptionModelTest {
    @Test
    fun `should use load balancer to choose node`() {
        val mockModel = mockk<AudioTranscriptionModel>()
        every { mockModel.transcribe(any<AudioTranscriptionRequest>()) } returns
            AudioTranscriptionResponse.from("transcribed")

        val node = AudioTranscriptionModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbAudio = LoadBalancedAudioTranscriptionModel(lb, maxRetries = 1)

        val result = lbAudio.transcribe(mockk())
        result.assert().isNotNull()
        verify(exactly = 1) { mockModel.transcribe(any<AudioTranscriptionRequest>()) }
    }

    @Test
    fun `should throw AllNodesUnavailableError when all nodes fail`() {
        val failingModel = mockk<AudioTranscriptionModel>()
        every { failingModel.transcribe(any<AudioTranscriptionRequest>()) } throws RuntimeException("error")

        val node = AudioTranscriptionModelNode("node-1", model = failingModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbAudio = LoadBalancedAudioTranscriptionModel(lb, maxRetries = 1)

        assertThrows<AllNodesUnavailableError> {
            lbAudio.transcribe(mockk())
        }
    }
}
