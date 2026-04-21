package me.ahoo.cobal.springai

import me.ahoo.cobal.AbstractLoadBalancedModel
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.springai.model.EmbeddingModelNode
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

class LoadBalancedEmbeddingModel(
    loadBalancer: LoadBalancer<EmbeddingModelNode>,
    maxRetries: Int = 3
) : AbstractLoadBalancedModel<EmbeddingModelNode, EmbeddingModel>(loadBalancer, maxRetries, SpringAiErrorConverter),
    EmbeddingModel {

    override fun embed(document: Document): FloatArray =
        executeWithRetry { it.embed(document) }

    override fun call(request: EmbeddingRequest): EmbeddingResponse =
        executeWithRetry { it.call(request) }
}
