package me.ahoo.cobal.springai

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.springai.model.AudioTranscriptionModelNode
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse
import org.springframework.ai.audio.transcription.TranscriptionModel

@Suppress("TooGenericExceptionCaught")
class LoadBalancedAudioTranscriptionModel(
    private val loadBalancer: LoadBalancer<AudioTranscriptionModelNode>,
    private val maxRetries: Int = 3
) : TranscriptionModel {

    override fun call(transcriptionPrompt: AudioTranscriptionPrompt): AudioTranscriptionResponse {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.call(transcriptionPrompt)
            } catch (e: Exception) {
                val nodeError = toNodeError(e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }

    companion object {
        fun toNodeError(e: Exception): NodeError {
            val category = when {
                e.message?.contains("429") == true -> ErrorCategory.RATE_LIMITED
                e.message?.contains("401") == true || e.message?.contains("403") == true -> ErrorCategory.AUTHENTICATION
                e.message?.contains("400") == true -> ErrorCategory.INVALID_REQUEST
                else -> ErrorCategory.SERVER_ERROR
            }
            return NodeError(category, e)
        }
    }
}
