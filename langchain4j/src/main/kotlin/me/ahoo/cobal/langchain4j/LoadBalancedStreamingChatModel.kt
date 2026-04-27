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

/** Node type for [StreamingChatModel] endpoints. */
typealias StreamingChatModelNode = DefaultModelNode<StreamingChatModel>

/**
 * Load-balanced [StreamingChatModel] that distributes streaming chat requests across multiple endpoints.
 *
 * Unlike the synchronous models, this class implements its own callback-based retry loop because
 * [StreamingChatModel] is callback-driven (via [StreamingChatResponseHandler]) and cannot use
 * the synchronous [LoadBalancer.execute] extension.
 *
 * On each attempt: acquires circuit breaker permission, dispatches to the selected node's model
 * with a [RetryingHandler], and records success/failure with timing.
 * [InvalidRequestError] short-circuits immediately — bad requests won't succeed on another node.
 *
 * @param loadBalancer the load balancer managing streaming chat model nodes
 * @param maxAttempts maximum retry attempts; defaults to the number of available nodes when set to 0
 * @param delegate used for Kotlin `by` delegation to inherit default method implementations
 */
class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val maxAttempts: Int = 0,
    private val delegate: StreamingChatModel = loadBalancer.states.first().node.model,
) : StreamingChatModel by delegate {

    private fun resolveAttempts(): Int =
        if (maxAttempts > 0) maxAttempts else loadBalancer.availableStates.size

    /**
     * [StreamingChatResponseHandler] that records success/failure timing on the [candidate] node state
     * and retries on retriable errors via [doChatWithRetry].
     */
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
