package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.chat.StreamingChatModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class StreamingChatModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: StreamingChatModel
) : ModelNode<StreamingChatModel>
