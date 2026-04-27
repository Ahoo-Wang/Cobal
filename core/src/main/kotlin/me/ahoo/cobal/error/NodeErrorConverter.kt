package me.ahoo.cobal.error

import me.ahoo.cobal.NodeId

/**
 * Converts a generic [Throwable] into a typed [NodeError].
 *
 * Framework-specific modules (LangChain4j, Spring AI) provide their own implementations
 * to map framework exceptions to the [NodeError] hierarchy.
 */
fun interface NodeErrorConverter {
    fun convert(nodeId: NodeId, error: Throwable): NodeError

    companion object {
        /** Fallback converter that wraps all errors as [NodeError]. */
        val Default = NodeErrorConverter { nodeId, error -> NodeError(nodeId, error.message, error) }
    }
}
