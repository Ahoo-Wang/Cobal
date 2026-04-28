package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.moderation.ModerationModel
import dev.langchain4j.model.moderation.ModerationRequest
import dev.langchain4j.model.moderation.ModerationResponse
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

/** Node type for [ModerationModel] endpoints. */
typealias ModerationModelNode = DefaultModelNode<ModerationModel>

/**
 * Load-balanced [ModerationModel] that distributes moderation requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [LangChain4JNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedModerationModel(
    private val loadBalancer: LoadBalancer<ModerationModelNode>,
    private val delegate: ModerationModel = loadBalancer.states.first().node.model,
) : ModerationModel by delegate {

    override fun doModerate(request: ModerationRequest): ModerationResponse =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.doModerate(request) }
}
