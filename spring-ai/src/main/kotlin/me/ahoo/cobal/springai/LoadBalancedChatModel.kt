package me.ahoo.cobal.springai

import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.springai.model.ChatModelNode
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux

class LoadBalancedChatModel(
    loadBalancer: LoadBalancer<ChatModelNode>,
    maxAttempts: Int = 3
) : AbstractLoadBalancedModel<ChatModelNode, ChatModel>(loadBalancer, maxAttempts, SpringAiErrorConverter),
    ChatModel {

    override fun call(prompt: Prompt): ChatResponse =
        executeWithRetry { it.call(prompt) }

    override fun stream(prompt: Prompt): Flux<ChatResponse> {
        return doStreamWithRetry(prompt, maxAttempts)
    }

    private fun doStreamWithRetry(prompt: Prompt, remainingRetries: Int): Flux<ChatResponse> {
        if (remainingRetries <= 0) {
            return Flux.error(AllNodesUnavailableError(loadBalancer.id))
        }

        val selected = loadBalancer.choose()
        val emitted = java.util.concurrent.atomic.AtomicBoolean(false)

        return selected.node.model.stream(prompt)
            .doOnNext { emitted.set(true) }
            .doOnComplete { selected.onSuccess() }
            .onErrorResume { error ->
                if (emitted.get()) {
                    Flux.error(error)
                } else {
                    val nodeError = SpringAiErrorConverter.convert(selected.node.id, error)
                    selected.onError(nodeError)
                    doStreamWithRetry(prompt, remainingRetries - 1)
                }
            }
    }
}
