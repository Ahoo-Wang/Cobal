package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

/** Node type for Spring AI [EmbeddingModel] endpoints. */
typealias EmbeddingModelNode = DefaultModelNode<EmbeddingModel>

/**
 * Load-balanced [EmbeddingModel] that distributes embedding requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [SpringAiNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedEmbeddingModel(
    private val loadBalancer: LoadBalancer<EmbeddingModelNode>,
    private val delegate: EmbeddingModel = loadBalancer.states.first().node.model,
) : EmbeddingModel by delegate {

    override fun call(request: EmbeddingRequest): EmbeddingResponse =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(request) }

    override fun embed(document: Document): FloatArray =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.embed(document) }
}
