package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute
import org.springframework.ai.moderation.ModerationModel
import org.springframework.ai.moderation.ModerationPrompt
import org.springframework.ai.moderation.ModerationResponse

/** Node type for Spring AI [ModerationModel] endpoints. */
typealias ModerationModelNode = DefaultModelNode<ModerationModel>

/**
 * Load-balanced [ModerationModel] that distributes moderation requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [SpringAiNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedModerationModel(
    private val loadBalancer: LoadBalancer<ModerationModelNode>,
    private val delegate: ModerationModel = loadBalancer.states.first().node.model,
) : ModerationModel by delegate {

    override fun call(prompt: ModerationPrompt): ModerationResponse =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(prompt) }
}
