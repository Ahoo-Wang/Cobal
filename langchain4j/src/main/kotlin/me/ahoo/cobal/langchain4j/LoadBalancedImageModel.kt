package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.image.Image
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

/** Node type for [ImageModel] endpoints. */
typealias ImageModelNode = DefaultModelNode<ImageModel>

/**
 * Load-balanced [ImageModel] that distributes image generation requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [LangChain4JNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedImageModel(
    private val loadBalancer: LoadBalancer<ImageModelNode>,
    private val delegate: ImageModel = loadBalancer.states.first().node.model,
) : ImageModel by delegate {

    override fun generate(prompt: String): Response<Image> =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.generate(prompt) }
}
