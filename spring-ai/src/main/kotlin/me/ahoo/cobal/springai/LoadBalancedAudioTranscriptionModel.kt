package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse
import org.springframework.ai.audio.transcription.TranscriptionModel

/** Node type for Spring AI [TranscriptionModel] endpoints. */
typealias AudioTranscriptionModelNode = DefaultModelNode<TranscriptionModel>

/**
 * Load-balanced [TranscriptionModel] that distributes audio transcription requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [SpringAiNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedAudioTranscriptionModel(
    private val loadBalancer: LoadBalancer<AudioTranscriptionModelNode>,
    private val delegate: TranscriptionModel = loadBalancer.states.first().node.model,
) : TranscriptionModel by delegate {

    override fun call(prompt: AudioTranscriptionPrompt): AudioTranscriptionResponse =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(prompt) }
}
