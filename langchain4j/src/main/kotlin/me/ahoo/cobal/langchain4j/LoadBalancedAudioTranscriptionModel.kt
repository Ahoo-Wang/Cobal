package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.audio.AudioTranscriptionModel
import dev.langchain4j.model.audio.AudioTranscriptionRequest
import dev.langchain4j.model.audio.AudioTranscriptionResponse
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.CobalError
import me.ahoo.cobal.InvalidRequestError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.ServerError
import me.ahoo.cobal.TimeoutError
import me.ahoo.cobal.langchain4j.model.AudioTranscriptionModelNode

class LoadBalancedAudioTranscriptionModel(
    private val loadBalancer: LoadBalancer<AudioTranscriptionModelNode>,
    private val maxRetries: Int = 3
) : AudioTranscriptionModel {

    override fun transcribe(request: AudioTranscriptionRequest): AudioTranscriptionResponse {
        repeat(maxRetries) { attempt ->
            val selected = loadBalancer.choose()
            @Suppress("TooGenericExceptionCaught")
            try {
                return selected.node.model.transcribe(request)
            } catch (e: Throwable) {
                val nodeError = toNodeError(selected.node.id, e as? Exception ?: RuntimeException(e.message, e))
                selected.onFailure(nodeError)
                if (attempt == maxRetries - 1) {
                    throw AllNodesUnavailableError(loadBalancer.id)
                }
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }

    companion object {
        fun toNodeError(nodeId: String, e: Exception): CobalError {
            return when (e) {
                is dev.langchain4j.exception.RateLimitException -> RateLimitError(nodeId, e)
                is dev.langchain4j.exception.InvalidRequestException -> InvalidRequestError(nodeId, e)
                is dev.langchain4j.exception.AuthenticationException -> AuthenticationError(nodeId, e)
                is dev.langchain4j.exception.TimeoutException -> TimeoutError(nodeId, e)
                else -> ServerError(nodeId, e)
            }
        }
    }
}
