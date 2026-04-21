package me.ahoo.cobal.springai

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.springai.model.ImageModelNode
import org.springframework.ai.image.ImageModel
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.image.ImageResponse

@Suppress("TooGenericExceptionCaught")
class LoadBalancedImageModel(
    private val loadBalancer: LoadBalancer<ImageModelNode>,
    private val maxRetries: Int = 3
) : ImageModel {

    override fun call(prompt: ImagePrompt): ImageResponse {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.call(prompt)
            } catch (e: Exception) {
                val nodeError = toNodeError(e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }

    companion object {
        fun toNodeError(e: Exception): NodeError {
            val category = when {
                e.message?.contains("429") == true -> ErrorCategory.RATE_LIMITED
                e.message?.contains("401") == true || e.message?.contains("403") == true -> ErrorCategory.AUTHENTICATION
                e.message?.contains("400") == true -> ErrorCategory.INVALID_REQUEST
                else -> ErrorCategory.SERVER_ERROR
            }
            return NodeError(category, e)
        }
    }
}
