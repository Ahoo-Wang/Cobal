package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.langchain4j.model.StreamingChatModelNode

class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val maxAttempts: Int = 3,
) : StreamingChatModel {

    override fun chat(prompt: String, handler: StreamingChatResponseHandler) {
        doChatWithRetry(prompt, handler, maxAttempts)
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
                selected.succeed()
                handler.onCompleteResponse(finalResponse)
            }

            override fun onError(error: Throwable) {
                val nodeError = LangChain4jErrorConverter.convert(selected.node.id, error)
                selected.fail(nodeError)
                doChatWithRetry(prompt, handler, remainingRetries - 1)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            selected.node.model.chat(prompt, retryingHandler)
        } catch (e: Exception) {
            val nodeError = LangChain4jErrorConverter.convert(selected.node.id, e)
            selected.fail(nodeError)
            doChatWithRetry(prompt, handler, remainingRetries - 1)
        }
    }
}
