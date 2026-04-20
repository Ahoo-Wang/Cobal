package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.AllNodesUnavailableException
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.langchain4j.model.EmbeddingModelNode

class LoadBalancedEmbeddingModel(
    private val loadBalancer: LoadBalancer<EmbeddingModelNode>,
    private val maxRetries: Int = 3
) : EmbeddingModel {

    override fun embedAll(textSegments: List<TextSegment>): Response<List<Embedding>> {
        repeat(maxRetries) { attempt ->
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.embedAll(textSegments)
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
