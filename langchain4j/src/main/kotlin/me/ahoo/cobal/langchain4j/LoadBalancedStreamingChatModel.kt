package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.error.throwIfInvalidRequest
import me.ahoo.cobal.state.NodeState

typealias StreamingChatModelNode = DefaultModelNode<StreamingChatModel>

class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val maxAttempts: Int = loadBalancer.availableStates.size,
) : StreamingChatModel {

    private inner class RetryingHandler(
        private val prompt: String,
        private val start: Long,
        private val remainingRetries: Int,
        private val candidate: NodeState<StreamingChatModelNode>,
        private val delegate: StreamingChatResponseHandler,
    ) : StreamingChatResponseHandler by delegate {
        override fun onCompleteResponse(finalResponse: ChatResponse) {
            val duration = candidate.currentTimestamp - start
            candidate.onResult(duration, candidate.timestampUnit, finalResponse)
            delegate.onCompleteResponse(finalResponse)
        }

        override fun onError(error: Throwable) {
            val nodeError = LangChain4JNodeErrorConverter.convert(candidate.node.id, error)
            val duration = candidate.currentTimestamp - start
            candidate.onError(duration, candidate.timestampUnit, nodeError)
            if (nodeError !is InvalidRequestError) {
                doChatWithRetry(prompt, delegate, remainingRetries - 1)
                return
            }
            delegate.onError(error)
        }
    }

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

        val candidate = loadBalancer.choose()
        val acquired = candidate.tryAcquirePermission()
        if (!acquired) {
            doChatWithRetry(prompt, handler, remainingRetries - 1)
            return
        }

        val start = candidate.currentTimestamp
        val retryingHandler = RetryingHandler(prompt, start, remainingRetries, candidate, handler)

        @Suppress("TooGenericExceptionCaught")
        try {
            candidate.node.model.chat(prompt, retryingHandler)
        } catch (e: Exception) {
            val nodeError = LangChain4JNodeErrorConverter.convert(candidate.node.id, e)
            val duration = candidate.currentTimestamp - start
            candidate.onError(duration, candidate.timestampUnit, nodeError)
            nodeError.throwIfInvalidRequest()
            doChatWithRetry(prompt, handler, remainingRetries - 1)
        }
    }
}
