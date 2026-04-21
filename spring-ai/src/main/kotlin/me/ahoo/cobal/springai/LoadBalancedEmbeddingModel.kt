package me.ahoo.cobal.springai

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.CobalError
import me.ahoo.cobal.InvalidRequestError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.ServerError
import me.ahoo.cobal.springai.model.EmbeddingModelNode
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

@Suppress("TooGenericExceptionCaught")
class LoadBalancedEmbeddingModel(
    private val loadBalancer: LoadBalancer<EmbeddingModelNode>,
    private val maxRetries: Int = 3
) : EmbeddingModel {

    override fun embed(document: Document): FloatArray {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.embed(document)
            } catch (e: Exception) {
                val nodeError = toNodeError(selected.node.id, e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun call(request: EmbeddingRequest): EmbeddingResponse {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.call(request)
            } catch (e: Exception) {
                val nodeError = toNodeError(selected.node.id, e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }

    companion object {
        fun toNodeError(nodeId: String, e: Exception): CobalError {
            return when {
                e.message?.contains("429") == true -> RateLimitError(nodeId, e)
                e.message?.contains("401") == true || e.message?.contains("403") == true -> AuthenticationError(
                    nodeId,
                    e
                )
                e.message?.contains("400") == true -> InvalidRequestError(nodeId, e)
                else -> ServerError(nodeId, e)
            }
        }
    }
}
