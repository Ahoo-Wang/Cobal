package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.isInvalidRequest
import me.ahoo.cobal.error.throwIfInvalidRequest
import me.ahoo.cobal.state.NodeState

typealias StreamingChatModelNode = DefaultModelNode<StreamingChatModel>

class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val maxAttempts: Int = 0,
    private val delegate: StreamingChatModel = loadBalancer.states.first().node.model,
) : StreamingChatModel by delegate {

    private fun resolveAttempts(): Int =
        if (maxAttempts > 0) maxAttempts else loadBalancer.availableStates.size

    private inner class RetryingHandler(
        private val request: ChatRequest,
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
            if (nodeError.isInvalidRequest.not()) {
                doChatWithRetry(request, this@RetryingHandler.delegate, remainingRetries - 1)
            } else {
                delegate.onError(nodeError)
            }
        }
    }

    override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
        doChatWithRetry(request, handler, resolveAttempts())
    }

    private fun doChatWithRetry(
        request: ChatRequest,
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
            doChatWithRetry(request, handler, remainingRetries - 1)
            return
        }

        val start = candidate.currentTimestamp
        val retryingHandler = RetryingHandler(request, start, remainingRetries, candidate, handler)

        @Suppress("TooGenericExceptionCaught")
        try {
            candidate.node.model.chat(request, retryingHandler)
        } catch (e: Exception) {
            val nodeError = LangChain4JNodeErrorConverter.convert(candidate.node.id, e)
            val duration = candidate.currentTimestamp - start
            candidate.onError(duration, candidate.timestampUnit, nodeError)
            nodeError.throwIfInvalidRequest()
            doChatWithRetry(request, handler, remainingRetries - 1)
        }
    }
}
