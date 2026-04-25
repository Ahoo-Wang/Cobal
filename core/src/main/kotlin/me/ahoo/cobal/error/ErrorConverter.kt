package me.ahoo.cobal.error

import me.ahoo.cobal.NodeId

/**
 * Converts a generic [Throwable] into a typed [CobalError].
 *
 * Framework-specific modules (LangChain4j, Spring AI) provide their own implementations
 * to map framework exceptions to the [CobalError] hierarchy.
 */
fun interface ErrorConverter {
    fun convert(nodeId: NodeId, error: Throwable): CobalError

    companion object {
        /** Fallback converter that wraps all errors as [NodeError]. */
        val Default = ErrorConverter { nodeId, error -> NodeError(nodeId, error.message, error) }
    }
}
