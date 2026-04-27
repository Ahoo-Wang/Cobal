package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.image.Image
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

typealias ImageModelNode = DefaultModelNode<ImageModel>

class LoadBalancedImageModel(
    private val loadBalancer: LoadBalancer<ImageModelNode>,
    private val delegate: ImageModel = loadBalancer.states.first().node.model,
) : ImageModel by delegate {

    override fun generate(prompt: String): Response<Image> =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.generate(prompt) }
}
