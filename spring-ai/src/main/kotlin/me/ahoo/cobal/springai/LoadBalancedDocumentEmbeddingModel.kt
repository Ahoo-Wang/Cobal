package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute
import org.springframework.ai.embedding.DocumentEmbeddingModel
import org.springframework.ai.embedding.DocumentEmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

/** Node type for Spring AI [DocumentEmbeddingModel] endpoints. */
typealias DocumentEmbeddingModelNode = DefaultModelNode<DocumentEmbeddingModel>

/**
 * Load-balanced [DocumentEmbeddingModel] that distributes document embedding requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [SpringAiNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedDocumentEmbeddingModel(
    private val loadBalancer: LoadBalancer<DocumentEmbeddingModelNode>,
    private val delegate: DocumentEmbeddingModel = loadBalancer.states.first().node.model,
) : DocumentEmbeddingModel by delegate {

    override fun call(request: DocumentEmbeddingRequest): EmbeddingResponse =
        loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(request) }
}
