package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

/** Node type for [EmbeddingModel] endpoints. */
typealias EmbeddingModelNode = DefaultModelNode<EmbeddingModel>

/**
 * Load-balanced [EmbeddingModel] that distributes embedding requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [LangChain4JNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedEmbeddingModel(
    private val loadBalancer: LoadBalancer<EmbeddingModelNode>,
    private val delegate: EmbeddingModel = loadBalancer.states.first().node.model,
) : EmbeddingModel by delegate {

    override fun embedAll(textSegments: List<TextSegment>): Response<List<Embedding>> =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.embedAll(textSegments) }
}
