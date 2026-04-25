package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError

typealias StreamingChatModelNode = DefaultModelNode<StreamingChatModel>

class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val maxAttempts: Int = loadBalancer.availableStates.size,
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
        val start = selected.currentTimestamp
        val retryingHandler = object : StreamingChatResponseHandler {
            override fun onCompleteResponse(finalResponse: ChatResponse) {
                val duration = selected.currentTimestamp - start
                selected.onResult(duration, selected.timestampUnit, finalResponse)
                handler.onCompleteResponse(finalResponse)
            }

            override fun onError(error: Throwable) {
                val nodeError = LangChain4JNodeErrorConverter.convert(selected.node.id, error)
                val duration = selected.currentTimestamp - start
                selected.onError(duration, selected.timestampUnit, nodeError)
                doChatWithRetry(prompt, handler, remainingRetries - 1)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            selected.node.model.chat(prompt, retryingHandler)
        } catch (e: Exception) {
            val nodeError = LangChain4JNodeErrorConverter.convert(selected.node.id, e)
            val duration = selected.currentTimestamp - start
            selected.onError(duration, selected.timestampUnit, nodeError)
            doChatWithRetry(prompt, handler, remainingRetries - 1)
        }
    }
}
