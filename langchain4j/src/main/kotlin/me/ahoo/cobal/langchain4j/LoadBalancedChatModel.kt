package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import me.ahoo.cobal.AllNodesUnavailableException
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.langchain4j.model.ChatModelNode

class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ChatModelNode>,
    private val maxRetries: Int = 3
) : ChatModel {

    override fun chat(request: ChatRequest): ChatResponse {
        repeat(maxRetries) { attempt ->
            val selected = loadBalancer.choose()
            @Suppress("TooGenericExceptionCaught")
            try {
                return selected.node.model.chat(request)
            } catch (e: Throwable) {
                val nodeError = toNodeError(e as? Exception ?: RuntimeException(e.message, e))
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
