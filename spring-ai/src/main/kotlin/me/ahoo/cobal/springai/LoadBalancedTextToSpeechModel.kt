package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute
import org.springframework.ai.audio.tts.TextToSpeechModel
import org.springframework.ai.audio.tts.TextToSpeechPrompt
import org.springframework.ai.audio.tts.TextToSpeechResponse
import reactor.core.publisher.Flux

/** Node type for Spring AI [TextToSpeechModel] endpoints. */
typealias TextToSpeechModelNode = DefaultModelNode<TextToSpeechModel>

/**
 * Load-balanced [TextToSpeechModel] that distributes TTS requests across multiple endpoints.
 *
 * [call] delegates to [LoadBalancer.execute] for synchronous retry.
 * [stream] uses [streamExecute] for reactive Flux-based retry with emission tracking —
 * does not retry after data has been emitted to the subscriber.
 *
 * @param loadBalancer the load balancer managing TTS model nodes
 * @param maxAttempts maximum retry attempts; defaults to the number of available nodes when set to 0
 * @param delegate used for Kotlin `by` delegation to inherit default method implementations
 */
class LoadBalancedTextToSpeechModel(
    private val loadBalancer: LoadBalancer<TextToSpeechModelNode>,
    private val maxAttempts: Int = 0,
    private val delegate: TextToSpeechModel = loadBalancer.states.first().node.model,
) : TextToSpeechModel by delegate {

    private fun resolveAttempts(): Int =
        if (maxAttempts > 0) maxAttempts else loadBalancer.availableStates.size

    override fun call(prompt: TextToSpeechPrompt): TextToSpeechResponse =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(prompt) }

    override fun stream(prompt: TextToSpeechPrompt): Flux<TextToSpeechResponse> =
        loadBalancer.streamExecute(SpringAiNodeErrorConverter, resolveAttempts()) { it.stream(prompt) }
}
