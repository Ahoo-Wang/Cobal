package me.ahoo.cobal.langchain4j

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.langchain4j.model.EmbeddingModelNode

class LoadBalancedEmbeddingModel(
    loadBalancer: LoadBalancer<EmbeddingModelNode>,
) : AbstractLoadBalancedModel<EmbeddingModelNode, EmbeddingModel>(loadBalancer, LangChain4JNodeErrorConverter),
    EmbeddingModel {
    override fun embedAll(textSegments: List<TextSegment>): Response<List<Embedding>> =
        executeWithRetry { it.embedAll(textSegments) }
}
