package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.CobalError
import me.ahoo.cobal.InvalidRequestError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.ServerError
import me.ahoo.cobal.TimeoutError
import me.ahoo.cobal.langchain4j.model.StreamingChatModelNode

class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val maxRetries: Int = 3,
) : StreamingChatModel {

    override fun chat(prompt: String, handler: StreamingChatResponseHandler) {
        doChatWithRetry(prompt, handler, maxRetries)
    }

    private fun doChatWithRetry(
        prompt: String,
        handler: StreamingChatResponseHandler,
        remainingRetries: Int,
    ) {
        if (remainingRetries <= 0) {
            handler.onError(AllNodesUnavailableError(loadBalancer.id))
            return
        }

        val selected = loadBalancer.choose()

        val retryingHandler = object : StreamingChatResponseHandler {
            override fun onCompleteResponse(finalResponse: ChatResponse) {
                handler.onCompleteResponse(finalResponse)
            }

            override fun onError(error: Throwable) {
                val nodeError =
                    toNodeError(selected.node.id, error as? Exception ?: RuntimeException(error.message, error))
                selected.onFailure(nodeError)
                doChatWithRetry(prompt, handler, remainingRetries - 1)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            selected.node.model.chat(prompt, retryingHandler)
        } catch (e: Throwable) {
            val nodeError = LangChain4jErrorConverter.convert(selected.node.id, e)
            selected.onFailure(nodeError)
            doChatWithRetry(prompt, handler, remainingRetries - 1)
        }
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
