package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.image.ImageModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class ImageModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: ImageModel
) : ModelNode<ImageModel>