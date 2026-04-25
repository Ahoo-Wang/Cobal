package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.image.Image
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer

typealias ImageModelNode = DefaultModelNode<ImageModel>

class LoadBalancedImageModel(
    loadBalancer: LoadBalancer<ImageModelNode>,
) : AbstractLoadBalancedModel<ImageModelNode, ImageModel>(loadBalancer, LangChain4JNodeErrorConverter),
    ImageModel {

    override fun generate(prompt: String): Response<Image> =
        executeWithRetry { it.generate(prompt) }
}
