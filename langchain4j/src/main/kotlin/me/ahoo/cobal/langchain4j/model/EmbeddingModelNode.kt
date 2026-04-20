package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.embedding.EmbeddingModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class EmbeddingModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: EmbeddingModel
) : ModelNode<EmbeddingModel>
