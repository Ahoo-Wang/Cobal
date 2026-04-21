package me.ahoo.cobal.springai

import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.CobalError
import me.ahoo.cobal.ErrorConverter
import me.ahoo.cobal.InvalidRequestError
import me.ahoo.cobal.NetworkError
import me.ahoo.cobal.NodeError
import me.ahoo.cobal.NodeId
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.ServerError
import me.ahoo.cobal.TimeoutError

object SpringAiErrorConverter : ErrorConverter {
    private const val MAX_CAUSE_DEPTH = 5

    override fun convert(nodeId: NodeId, error: Throwable): CobalError {
        return matchFromChain(nodeId, error) ?: NodeError(nodeId, error.message, error)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun matchFromChain(nodeId: NodeId, error: Throwable, depth: Int = 0): CobalError? {
        if (depth > MAX_CAUSE_DEPTH) return null
        val msg = error.message
        return when {
            msg.containsStatus("429") -> RateLimitError(nodeId, error)
            msg.containsStatus("401") || msg.containsStatus("403") -> AuthenticationError(nodeId, error)
            msg.containsStatus("400") -> InvalidRequestError(nodeId, error)
            isServerErrorStatus(msg) -> ServerError(nodeId, error)
            msg?.contains("timeout", ignoreCase = true) == true -> TimeoutError(nodeId, error)
            isNetworkError(msg, error) -> NetworkError(nodeId, error)
            else -> {
                val cause = error.cause
                if (cause != null && cause !== error) matchFromChain(nodeId, cause, depth + 1) else null
            }
        }
    }

    private fun String?.containsStatus(status: String): Boolean =
        this != null && (contains(" $status ") || contains("$status:") || contains("$status\n") || contains("HTTP $status") || contains("Status $status"))

    private fun isServerErrorStatus(msg: String?): Boolean =
        msg.containsStatus("500") ||
            msg.containsStatus("502") ||
            msg.containsStatus("503") ||
            msg.containsStatus("504")

    private fun isNetworkError(msg: String?, error: Throwable): Boolean =
        msg?.contains("network", ignoreCase = true) == true ||
            msg?.contains("connection", ignoreCase = true) == true ||
            error is java.net.ConnectException ||
            error is java.net.SocketTimeoutException ||
            error is java.io.IOException
}
