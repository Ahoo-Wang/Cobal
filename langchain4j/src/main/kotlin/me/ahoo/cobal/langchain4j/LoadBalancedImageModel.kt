package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.image.Image
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.langchain4j.model.ImageModelNode

class LoadBalancedImageModel(
    private val loadBalancer: LoadBalancer<ImageModelNode>,
    private val maxRetries: Int = 3
) : ImageModel {

    override fun generate(prompt: String): Response<Image> {
        repeat(maxRetries) { attempt ->
            val selected = loadBalancer.choose()
            @Suppress("TooGenericExceptionCaught")
            try {
                return selected.node.model.generate(prompt)
            } catch (e: Throwable) {
                val nodeError = toNodeError(e as? Exception ?: RuntimeException(e.message, e))
                selected.onFailure(nodeError)
                if (attempt == maxRetries - 1) {
                    throw AllNodesUnavailableError(loadBalancer.id)
                }
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
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
