package me.ahoo.cobal.springai

import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.springai.model.AudioTranscriptionModelNode
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse
import org.springframework.ai.audio.transcription.TranscriptionModel

class LoadBalancedAudioTranscriptionModel(
    loadBalancer: LoadBalancer<AudioTranscriptionModelNode>,
    maxRetries: Int = 3
) : AbstractLoadBalancedModel<
    AudioTranscriptionModelNode,
    TranscriptionModel
    >(
    loadBalancer,
    maxRetries,
    SpringAiErrorConverter
),
    TranscriptionModel {

    override fun call(transcriptionPrompt: AudioTranscriptionPrompt): AudioTranscriptionResponse =
        executeWithRetry { it.call(transcriptionPrompt) }
}
