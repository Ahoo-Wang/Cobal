package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.CobalError
import me.ahoo.cobal.InvalidRequestError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.ServerError
import me.ahoo.cobal.TimeoutError
import me.ahoo.cobal.langchain4j.model.EmbeddingModelNode

class LoadBalancedEmbeddingModel(
    private val loadBalancer: LoadBalancer<EmbeddingModelNode>,
    private val maxRetries: Int = 3
) : EmbeddingModel {

    override fun embedAll(textSegments: List<TextSegment>): Response<List<Embedding>> {
        repeat(maxRetries) { attempt ->
            val selected = loadBalancer.choose()
            @Suppress("TooGenericExceptionCaught")
            try {
                return selected.node.model.embedAll(textSegments)
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
