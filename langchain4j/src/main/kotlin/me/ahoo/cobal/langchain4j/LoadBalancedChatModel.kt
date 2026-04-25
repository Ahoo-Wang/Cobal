package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.langchain4j.model.ChatModelNode

class LoadBalancedChatModel(
    loadBalancer: LoadBalancer<ChatModelNode>,
) : AbstractLoadBalancedModel<ChatModelNode, ChatModel>(loadBalancer, LangChain4JNodeErrorConverter),
    ChatModel {

    override fun chat(request: ChatRequest): ChatResponse =
        executeWithRetry { it.chat(request) }
}
