package me.ahoo.cobal.springai

import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.springai.model.ImageModelNode
import org.springframework.ai.image.ImageModel
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.image.ImageResponse

class LoadBalancedImageModel(
    loadBalancer: LoadBalancer<ImageModelNode>,
    maxAttempts: Int = 3
) : AbstractLoadBalancedModel<ImageModelNode, ImageModel>(loadBalancer, maxAttempts, SpringAiErrorConverter),
    ImageModel {

    override fun call(prompt: ImagePrompt): ImageResponse =
        executeWithRetry { it.call(prompt) }
}
