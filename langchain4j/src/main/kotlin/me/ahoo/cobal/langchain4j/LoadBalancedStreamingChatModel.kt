package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.NodeError
import me.ahoo.cobal.error.shortCircuitsRetry
import me.ahoo.cobal.state.NodeState

/** Node type for [StreamingChatModel] endpoints. */
typealias StreamingChatModelNode = DefaultModelNode<StreamingChatModel>

/**
 * Load-balanced [StreamingChatModel] that distributes streaming chat requests across multiple endpoints.
 *
 * Implements its own retry loop because [StreamingChatModel] is callback-driven (via
 * [StreamingChatResponseHandler]) and cannot use the synchronous [LoadBalancer.execute] extension.
 *
 * Retry is driven by a trampolining state machine ([RetryDriver]) — synchronous callbacks
 * (provider invokes `onError` from within `chat(...)`) drain via the outer `while` loop, while
 * asynchronous callbacks re-enter the loop once. Stack depth is bounded by 1 regardless of how
 * many nodes are retried.
 *
 * Explicit non-retriable errors short-circuit immediately. When every retry is exhausted, the
 * delegate handler receives a single [AllNodesUnavailableError] with all per-attempt failures
 * attached as suppressed exceptions.
 */
class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val delegate: StreamingChatModel = loadBalancer.states.first().node.model,
) : StreamingChatModel by delegate {

    override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
        RetryDriver(loadBalancer, request, handler).start()
    }

    private class RetryDriver(
        private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
        private val request: ChatRequest,
        private val handler: StreamingChatResponseHandler,
    ) {
        private val maxAttempts = loadBalancer.availableStates.size.coerceAtLeast(1)
        private val rejectionBudget = loadBalancer.states.size
        private val failures = mutableListOf<NodeError>()
        private var attempts = 0
        private var rejections = 0

        // True while `runLoop` is on the stack — signals callbacks to defer retry to the loop.
        @Volatile
        private var processing = false

        // Terminal state reached (success or non-retriable error); stops further retries.
        @Volatile
        private var terminated = false

        // Set by RetryingHandler.onError on a retriable failure; consumed by runLoop.
        @Volatile
        private var pendingRetry = false

        fun start() {
            runLoop()
        }

        private enum class StepOutcome { CONTINUE, ASYNC_WAIT, EXHAUSTED, TERMINATED }

        private fun runLoop() {
            processing = true
            try {
                while (true) {
                    when (step()) {
                        StepOutcome.CONTINUE -> {}
                        StepOutcome.ASYNC_WAIT -> return
                        StepOutcome.TERMINATED -> return
                        StepOutcome.EXHAUSTED -> {
                            fail()
                            return
                        }
                    }
                }
            } finally {
                processing = false
            }
        }

        @Suppress("TooGenericExceptionCaught", "ReturnCount")
        private fun step(): StepOutcome {
            if (terminated) return StepOutcome.TERMINATED
            if (attempts >= maxAttempts || loadBalancer.availableStates.isEmpty()) return StepOutcome.EXHAUSTED
            pendingRetry = false

            val candidate = try {
                loadBalancer.choose()
            } catch (_: AllNodesUnavailableError) {
                return StepOutcome.EXHAUSTED
            }

            if (!candidate.tryAcquirePermission()) {
                rejections++
                return if (rejections >= rejectionBudget) StepOutcome.EXHAUSTED else StepOutcome.CONTINUE
            }
            attempts++

            val start = candidate.currentTimestamp
            val retryingHandler = RetryingHandler(start, candidate)
            try {
                candidate.node.model.chat(request, retryingHandler)
            } catch (e: Exception) {
                val terminal = recordSynchronousFailure(candidate, start, e)
                return if (terminal) StepOutcome.TERMINATED else StepOutcome.CONTINUE
            }

            if (terminated) return StepOutcome.TERMINATED
            if (pendingRetry) return StepOutcome.CONTINUE
            // Asynchronous path: callback will eventually re-enter via runLoop().
            return StepOutcome.ASYNC_WAIT
        }

        /** Returns `true` if the failure terminates the retry (invalid request). */
        private fun recordSynchronousFailure(
            candidate: NodeState<StreamingChatModelNode>,
            start: Long,
            error: Throwable,
        ): Boolean {
            val nodeError = LangChain4JNodeErrorConverter.convert(candidate.node.id, error)
            if (nodeError.shortCircuitsRetry) {
                candidate.releasePermission()
                terminated = true
                handler.onError(nodeError)
                return true
            }
            val duration = candidate.currentTimestamp - start
            candidate.onError(duration, candidate.timestampUnit, nodeError)
            failures.add(nodeError)
            return false
        }

        private fun fail() {
            if (terminated) return
            terminated = true
            handler.onError(AllNodesUnavailableError(loadBalancer.id, failures))
        }

        private inner class RetryingHandler(
            private val start: Long,
            private val candidate: NodeState<StreamingChatModelNode>,
        ) : StreamingChatResponseHandler by handler {
            override fun onCompleteResponse(finalResponse: ChatResponse) {
                val duration = candidate.currentTimestamp - start
                candidate.onResult(duration, candidate.timestampUnit, finalResponse)
                terminated = true
                handler.onCompleteResponse(finalResponse)
            }

            override fun onError(error: Throwable) {
                val nodeError = LangChain4JNodeErrorConverter.convert(candidate.node.id, error)
                if (nodeError.shortCircuitsRetry) {
                    candidate.releasePermission()
                    terminated = true
                    handler.onError(nodeError)
                    return
                }
                val duration = candidate.currentTimestamp - start
                candidate.onError(duration, candidate.timestampUnit, nodeError)
                failures.add(nodeError)
                if (processing) {
                    // Synchronous callback inside model.chat(...); let runLoop pick this up.
                    pendingRetry = true
                } else {
                    // Asynchronous callback after model.chat returned; re-drive the loop.
                    runLoop()
                }
            }
        }
    }
}
