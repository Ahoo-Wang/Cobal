package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.audio.AudioTranscriptionModel
import dev.langchain4j.model.audio.AudioTranscriptionRequest
import dev.langchain4j.model.audio.AudioTranscriptionResponse
import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer

typealias AudioTranscriptionModelNode = DefaultModelNode<AudioTranscriptionModel>

class LoadBalancedAudioTranscriptionModel(
    loadBalancer: LoadBalancer<AudioTranscriptionModelNode>,
) : AbstractLoadBalancedModel<
    AudioTranscriptionModelNode,
    AudioTranscriptionModel
    >(
    loadBalancer,
    LangChain4JNodeErrorConverter
),
    AudioTranscriptionModel {

    override fun transcribe(request: AudioTranscriptionRequest): AudioTranscriptionResponse =
        executeWithRetry { it.transcribe(request) }
}
