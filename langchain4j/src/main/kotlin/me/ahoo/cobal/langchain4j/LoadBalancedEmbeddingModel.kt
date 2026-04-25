package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.execute

typealias EmbeddingModelNode = DefaultModelNode<EmbeddingModel>

class LoadBalancedEmbeddingModel(
    private val loadBalancer: LoadBalancer<EmbeddingModelNode>,
) : EmbeddingModel {
    override fun embedAll(textSegments: List<TextSegment>): Response<List<Embedding>> =
        loadBalancer.execute(LangChain4JNodeErrorConverter) { it.embedAll(textSegments) }
}
