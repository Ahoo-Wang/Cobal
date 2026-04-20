package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import me.ahoo.cobal.AllNodesUnavailableException
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.langchain4j.model.StreamingChatModelNode

class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val maxRetries: Int = 3
) : StreamingChatModel {

    override fun chat(prompt: String, handler: StreamingChatResponseHandler) {
        doChatWithRetry(prompt, handler, maxRetries)
    }

    private fun doChatWithRetry(
        prompt: String,
        handler: StreamingChatResponseHandler,
        remainingRetries: Int
    ) {
        if (remainingRetries <= 0) {
            handler.onError(AllNodesUnavailableException(loadBalancer.id))
            return
        }

        val selected = loadBalancer.choose()

        val retryingHandler = object : StreamingChatResponseHandler {
            override fun onCompleteResponse(finalResponse: ChatResponse) {
                handler.onCompleteResponse(finalResponse)
            }

            override fun onError(error: Throwable) {
                val nodeError = toNodeError(error as? Exception ?: RuntimeException(error.message, error))
                selected.onFailure(nodeError)
                doChatWithRetry(prompt, handler, remainingRetries - 1)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            selected.node.model.chat(prompt, retryingHandler)
        } catch (e: Throwable) {
            val nodeError = toNodeError(e as? Exception ?: RuntimeException(e.message, e))
            selected.onFailure(nodeError)
            doChatWithRetry(prompt, handler, remainingRetries - 1)
        }
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
