package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.output.Response
import dev.langchain4j.model.scoring.ScoringModel
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

/** Node type for [ScoringModel] endpoints. */
typealias ScoringModelNode = DefaultModelNode<ScoringModel>

/**
 * Load-balanced [ScoringModel] that distributes scoring requests across multiple endpoints.
 *
 * Delegates execution to [LoadBalancer.execute] with [LangChain4JNodeErrorConverter] for
 * automatic retry, circuit breaker integration, and error classification.
 */
class LoadBalancedScoringModel(
    private val loadBalancer: LoadBalancer<ScoringModelNode>,
    private val delegate: ScoringModel = loadBalancer.states.first().node.model,
) : ScoringModel by delegate {

    override fun scoreAll(segments: List<TextSegment>, query: String): Response<List<Double>> =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.scoreAll(segments, query) }
}
