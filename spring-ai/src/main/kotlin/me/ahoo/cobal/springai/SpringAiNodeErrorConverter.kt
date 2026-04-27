package me.ahoo.cobal.springai

import me.ahoo.cobal.NodeId
import me.ahoo.cobal.error.AuthenticationError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.error.NetworkError
import me.ahoo.cobal.error.NodeError
import me.ahoo.cobal.error.NodeErrorConverter
import me.ahoo.cobal.error.RateLimitError
import me.ahoo.cobal.error.ServerError
import me.ahoo.cobal.error.TimeoutError

/**
 * Spring AI-specific [NodeErrorConverter] that maps framework exceptions to the [NodeError] hierarchy.
 *
 * Spring AI does not provide typed exception classes — errors are generic [RuntimeException]
 * with HTTP status codes embedded in messages. This converter uses message pattern matching
 * with recursive cause chain unwrapping (up to 5 levels deep).
 */
object SpringAiNodeErrorConverter : NodeErrorConverter {
    private const val MAX_CAUSE_DEPTH = 5

    override fun convert(nodeId: NodeId, error: Throwable): NodeError {
        return matchFromChain(nodeId, error) ?: NodeError(nodeId, error.message, error)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun matchFromChain(nodeId: NodeId, error: Throwable, depth: Int = 0): NodeError? {
        if (depth > MAX_CAUSE_DEPTH) {
            return null
        }
        val msg = error.message
        return when {
            msg.containsStatus("429") -> RateLimitError(nodeId, error)
            msg.containsStatus("401") || msg.containsStatus("403") -> AuthenticationError(nodeId, error)
            msg.containsStatus("400") -> InvalidRequestError(nodeId, error)
            isServerErrorStatus(msg) -> ServerError(nodeId, error)
            msg?.contains("timeout", ignoreCase = true) == true -> TimeoutError(nodeId, error)
            isNetworkError(error) -> NetworkError(nodeId, error)
            else -> {
                val cause = error.cause
                if (cause != null && cause !== error) {
                    matchFromChain(nodeId, cause, depth + 1)
                } else {
                    null
                }
            }
        }
    }

    private fun String?.containsStatus(status: String): Boolean =
        this != null && (
            contains(" $status ") ||
                contains("$status:") ||
                contains("$status\n") ||
                contains("HTTP $status") ||
                contains("Status $status")
            )

    private fun isServerErrorStatus(msg: String?): Boolean =
        msg.containsStatus("500") ||
            msg.containsStatus("502") ||
            msg.containsStatus("503") ||
            msg.containsStatus("504")

    private fun isNetworkError(error: Throwable): Boolean =
        error is java.net.ConnectException ||
            error is java.net.SocketTimeoutException ||
            error is java.io.IOException
}
