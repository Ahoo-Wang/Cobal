package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.audio.AudioTranscriptionModel
import dev.langchain4j.model.audio.AudioTranscriptionRequest
import dev.langchain4j.model.audio.AudioTranscriptionResponse
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

typealias AudioTranscriptionModelNode = DefaultModelNode<AudioTranscriptionModel>

class LoadBalancedAudioTranscriptionModel(
    private val loadBalancer: LoadBalancer<AudioTranscriptionModelNode>,
    private val delegate: AudioTranscriptionModel = loadBalancer.states.first().node.model,
) : AudioTranscriptionModel by delegate {

    override fun transcribe(request: AudioTranscriptionRequest): AudioTranscriptionResponse =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.transcribe(request) }
}
