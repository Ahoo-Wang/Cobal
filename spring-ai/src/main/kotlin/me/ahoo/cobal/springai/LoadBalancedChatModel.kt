package me.ahoo.cobal.springai

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.CobalError
import me.ahoo.cobal.InvalidRequestError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.ServerError
import me.ahoo.cobal.springai.model.ChatModelNode
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt

@Suppress("TooGenericExceptionCaught")
class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ChatModelNode>,
    private val maxRetries: Int = 3
) : ChatModel {

    override fun call(prompt: Prompt): ChatResponse {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.call(prompt)
            } catch (e: Exception) {
                val nodeError = toNodeError(selected.node.id, e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }

    companion object {
        fun toNodeError(nodeId: String, e: Exception): CobalError {
            return when {
                e.message?.contains("429") == true -> RateLimitError(nodeId, e)
                e.message?.contains("401") == true || e.message?.contains("403") == true -> AuthenticationError(nodeId, e)
                e.message?.contains("400") == true -> InvalidRequestError(nodeId, e)
                else -> ServerError(nodeId, e)
            }
        }
    }
}
