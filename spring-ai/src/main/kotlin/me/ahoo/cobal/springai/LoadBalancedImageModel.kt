package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute
import org.springframework.ai.image.ImageModel
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.image.ImageResponse

/** Node type for Spring AI [ImageModel] endpoints. */
typealias ImageModelNode = DefaultModelNode<ImageModel>

/**
 * Load-balanced [ImageModel] that distributes image generation requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [SpringAiNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedImageModel(
    private val loadBalancer: LoadBalancer<ImageModelNode>,
    private val delegate: ImageModel = loadBalancer.states.first().node.model,
) : ImageModel by delegate {

    override fun call(prompt: ImagePrompt): ImageResponse =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(prompt) }
}
