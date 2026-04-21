package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.audio.AudioTranscriptionModel
import dev.langchain4j.model.audio.AudioTranscriptionRequest
import dev.langchain4j.model.audio.AudioTranscriptionResponse
import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.langchain4j.model.AudioTranscriptionModelNode

class LoadBalancedAudioTranscriptionModel(
    loadBalancer: LoadBalancer<AudioTranscriptionModelNode>,
    maxRetries: Int = 3
) : AbstractLoadBalancedModel<
    AudioTranscriptionModelNode,
    AudioTranscriptionModel
    >(
    loadBalancer,
    maxRetries,
    LangChain4jErrorConverter
),
    AudioTranscriptionModel {

    override fun transcribe(request: AudioTranscriptionRequest): AudioTranscriptionResponse =
        executeWithRetry { it.transcribe(request) }
}
