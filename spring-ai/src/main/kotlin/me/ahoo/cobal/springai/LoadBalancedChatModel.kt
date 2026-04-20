package me.ahoo.cobal.springai

import me.ahoo.cobal.AllNodesUnavailableException
import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.springai.model.ChatModelNode
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt

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
                val nodeError = toNodeError(e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableException(loadBalancer.id)
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
