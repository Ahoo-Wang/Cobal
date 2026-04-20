package me.ahoo.cobal

import kotlinx.coroutines.flow.Flow

typealias NodeId = String

interface Node {
    val id: NodeId
    val weight: Int
        get() = 1
}

interface NodeEvent

interface WatchableNode : Node {
    val watch: Flow<NodeEvent>
}
