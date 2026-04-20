package me.ahoo.cobal

typealias NodeId = String

interface Node {
    val id: NodeId
    val weight: Int
        get() = 1
}

interface ModelNode<MODEL> : Node {
    val model: MODEL
}
