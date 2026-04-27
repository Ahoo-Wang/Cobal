package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

/** Node type for [ChatModel] endpoints. */
typealias ChatModelNode = DefaultModelNode<ChatModel>

/**
 * Load-balanced [ChatModel] that distributes synchronous chat requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [LangChain4JNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ChatModelNode>,
    private val delegate: ChatModel = loadBalancer.states.first().node.model,
) : ChatModel by delegate {

    override fun doChat(request: ChatRequest): ChatResponse =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.chat(request) }
}
