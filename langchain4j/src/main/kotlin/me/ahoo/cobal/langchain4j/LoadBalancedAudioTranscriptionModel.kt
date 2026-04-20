package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.audio.AudioTranscriptionModel
import dev.langchain4j.model.audio.AudioTranscriptionRequest
import dev.langchain4j.model.audio.AudioTranscriptionResponse
import me.ahoo.cobal.AllNodesUnavailableException
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.langchain4j.model.AudioTranscriptionModelNode

class LoadBalancedAudioTranscriptionModel(
    private val loadBalancer: LoadBalancer<AudioTranscriptionModelNode>,
    private val maxRetries: Int = 3
) : AudioTranscriptionModel {

    override fun transcribe(request: AudioTranscriptionRequest): AudioTranscriptionResponse {
        repeat(maxRetries) { attempt ->
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.transcribe(request)
            } catch (e: Exception) {
                val nodeError = toNodeError(e)
                selected.onFailure(nodeError)
                if (attempt == maxRetries - 1) {
                    throw AllNodesUnavailableException(loadBalancer.id)
                }
            }
        }
        throw AllNodesUnavailableException(loadBalancer.id)
    }

    companion object {
        fun toNodeError(e: Exception): NodeError {
            val category = when (e) {
                is dev.langchain4j.exception.RateLimitException -> ErrorCategory.RATE_LIMITED
                is dev.langchain4j.exception.InvalidRequestException -> ErrorCategory.INVALID_REQUEST
                is dev.langchain4j.exception.AuthenticationException -> ErrorCategory.AUTHENTICATION
                is dev.langchain4j.exception.TimeoutException -> ErrorCategory.TIMEOUT
                else -> ErrorCategory.SERVER_ERROR
            }
            return NodeError(category, e)
        }
    }
}
