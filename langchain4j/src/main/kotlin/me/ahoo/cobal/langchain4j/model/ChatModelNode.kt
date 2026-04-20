package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.chat.ChatModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class ChatModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: ChatModel
) : ModelNode<ChatModel>