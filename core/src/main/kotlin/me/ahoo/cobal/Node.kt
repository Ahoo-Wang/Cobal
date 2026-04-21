package me.ahoo.cobal

typealias NodeId = String

interface Node {
    val id: NodeId
    val weight: Int
        get() = 1
}

data class DefaultNode(
    override val id: NodeId,
    override val weight: Int = 1,
) : Node

interface ModelNode<MODEL> : Node {
    val model: MODEL
}

class DefaultModelNode<MODEL>(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: MODEL,
) : ModelNode<MODEL>
