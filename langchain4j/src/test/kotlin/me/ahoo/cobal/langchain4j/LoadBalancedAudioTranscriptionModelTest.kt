package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.audio.AudioTranscriptionModel
import dev.langchain4j.model.audio.AudioTranscriptionRequest
import dev.langchain4j.model.audio.AudioTranscriptionResponse
import io.mockk.every
import io.mockk.mockk
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.langchain4j.model.AudioTranscriptionModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancedAudioTranscriptionModelTest {
    @Test
    fun `should compile and extend AudioTranscriptionModel`() {
        val mockModel = mockk<AudioTranscriptionModel>()
        every { mockModel.transcribe(any<AudioTranscriptionRequest>()) } returns
            AudioTranscriptionResponse.from("transcribed")

        val node = AudioTranscriptionModelNode("node-1", model = mockModel)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("lb", listOf(state))
        val lbAudio = LoadBalancedAudioTranscriptionModel(lb, maxRetries = 1)
        lbAudio.assert().isNotNull()
    }
}
