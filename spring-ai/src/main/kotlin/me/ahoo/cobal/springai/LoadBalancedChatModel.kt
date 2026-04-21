package me.ahoo.cobal.springai

import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.springai.model.ChatModelNode
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt

class LoadBalancedChatModel(
    loadBalancer: LoadBalancer<ChatModelNode>,
    maxRetries: Int = 3
) : AbstractLoadBalancedModel<ChatModelNode, ChatModel>(loadBalancer, maxRetries, SpringAiErrorConverter),
    ChatModel {

    override fun call(prompt: Prompt): ChatResponse =
        executeWithRetry { it.call(prompt) }
}
