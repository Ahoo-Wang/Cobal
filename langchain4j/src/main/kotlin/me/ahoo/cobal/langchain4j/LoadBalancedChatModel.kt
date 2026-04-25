package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

typealias ChatModelNode = DefaultModelNode<ChatModel>

class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ChatModelNode>,
) : ChatModel {

    override fun chat(request: ChatRequest): ChatResponse =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.chat(request) }
}
