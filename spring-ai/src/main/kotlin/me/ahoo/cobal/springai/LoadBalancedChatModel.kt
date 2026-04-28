package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux

/** Node type for Spring AI [ChatModel] endpoints. */
typealias ChatModelNode = DefaultModelNode<ChatModel>

/**
 * Load-balanced [ChatModel] that distributes both synchronous and streaming chat requests
 * across multiple endpoints.
 *
 * [call] delegates to [LoadBalancer.execute] for synchronous retry.
 * [stream] uses [streamExecute] for reactive Flux-based retry with emission tracking —
 * does not retry after data has been emitted to the subscriber.
 *
 * @param loadBalancer the load balancer managing chat model nodes
 * @param maxAttempts maximum retry attempts; defaults to the number of available nodes when set to 0
 * @param delegate used for Kotlin `by` delegation to inherit default method implementations
 */
class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ChatModelNode>,
    private val delegate: ChatModel = loadBalancer.states.first().node.model,
) : ChatModel by delegate {

    override fun call(prompt: Prompt): ChatResponse =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(prompt) }

    override fun stream(prompt: Prompt): Flux<ChatResponse> =
        loadBalancer.streamExecute(SpringAiNodeErrorConverter, loadBalancer.availableStates.size) { it.stream(prompt) }
}
