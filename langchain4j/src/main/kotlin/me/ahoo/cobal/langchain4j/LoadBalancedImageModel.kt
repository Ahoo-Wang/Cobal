package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.image.Image
import dev.langchain4j.model.image.ImageModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.langchain4j.model.ImageModelNode

class LoadBalancedImageModel(
    loadBalancer: LoadBalancer<ImageModelNode>,
    maxAttempts: Int = 3
) : AbstractLoadBalancedModel<ImageModelNode, ImageModel>(loadBalancer, maxAttempts, LangChain4jErrorConverter),
    ImageModel {

    override fun generate(prompt: String): Response<Image> =
        executeWithRetry { it.generate(prompt) }
}
