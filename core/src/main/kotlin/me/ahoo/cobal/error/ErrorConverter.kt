package me.ahoo.cobal.error

import me.ahoo.cobal.NodeId

fun interface ErrorConverter {
    fun convert(nodeId: NodeId, error: Throwable): CobalError

    companion object {
        val Default = ErrorConverter { nodeId, error -> NodeError(nodeId, error.message, error) }
    }
}
