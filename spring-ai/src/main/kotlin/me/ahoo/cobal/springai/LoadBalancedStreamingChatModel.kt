package me.ahoo.cobal.springai

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.springai.model.StreamingChatModelNode
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux

class LoadBalancedStreamingChatModel(
    private val loadBalancer: LoadBalancer<StreamingChatModelNode>,
    private val maxAttempts: Int = 3,
) : StreamingChatModel {

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
