package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.execute
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicBoolean

/** Node type for Spring AI [ChatModel] endpoints. */
typealias ChatModelNode = DefaultModelNode<ChatModel>

/**
 * Load-balanced [ChatModel] that distributes both synchronous and streaming chat requests
 * across multiple endpoints.
 *
 * [call] delegates to [LoadBalancer.execute] for synchronous retry.
 * [stream] implements reactive Flux-based retry with emission tracking — does not retry
 * after data has been emitted to the subscriber.
 *
 * @param loadBalancer the load balancer managing chat model nodes
 * @param maxAttempts maximum retry attempts; defaults to the number of available nodes when set to 0
 * @param delegate used for Kotlin `by` delegation to inherit default method implementations
 */
class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ChatModelNode>,
    private val maxAttempts: Int = 0,
    private val delegate: ChatModel = loadBalancer.states.first().node.model,
) : ChatModel by delegate {

    private fun resolveAttempts(): Int =
        if (maxAttempts > 0) maxAttempts else loadBalancer.availableStates.size

    override fun call(prompt: Prompt): ChatResponse =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(prompt) }

    override fun stream(prompt: Prompt): Flux<ChatResponse> {
        return doStreamWithRetry(prompt, resolveAttempts())
    }

    private fun doStreamWithRetry(prompt: Prompt, remainingRetries: Int): Flux<ChatResponse> {
        if (remainingRetries <= 0) {
            return Flux.error(AllNodesUnavailableError(loadBalancer.id))
        }

        val candidate = loadBalancer.choose()
        val acquired = candidate.tryAcquirePermission()
        if (!acquired) {
            return doStreamWithRetry(prompt, remainingRetries - 1)
        }

        val start = candidate.currentTimestamp
        val emitted = AtomicBoolean(false)

        return candidate.node.model.stream(prompt)
            .doOnNext { emitted.set(true) }
            .doOnComplete {
                val duration = candidate.currentTimestamp - start
                candidate.onResult(duration, candidate.timestampUnit, Unit)
            }
            .onErrorResume { error ->
                if (emitted.get()) {
                    Flux.error(error)
                } else {
                    val nodeError = SpringAiNodeErrorConverter.convert(candidate.node.id, error)
                    val duration = candidate.currentTimestamp - start
                    candidate.onError(duration, candidate.timestampUnit, nodeError)
                    if (nodeError is InvalidRequestError) {
                        Flux.error(nodeError)
                    } else {
                        doStreamWithRetry(prompt, remainingRetries - 1)
                    }
                }
            }
    }
}
