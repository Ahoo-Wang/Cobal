package me.ahoo.cobal.springai

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
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
                val nodeError = toNodeError(e)
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
