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
    override fun convert(nodeId: NodeId, error: Throwable): CobalError {
        return matchFromChain(nodeId, error) ?: NodeError(nodeId, error.message, error)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun matchFromChain(nodeId: NodeId, error: Throwable, depth: Int = 0): CobalError? {
        if (depth > 5) return null
        val msg = error.message
        return when {
            msg?.contains("429") == true -> RateLimitError(nodeId, error)
            msg?.contains("401") == true || msg?.contains("403") == true -> AuthenticationError(nodeId, error)
            msg?.contains("400") == true -> InvalidRequestError(nodeId, error)
            isServerErrorStatus(msg) -> ServerError(nodeId, error)
            msg?.contains("timeout", ignoreCase = true) == true -> TimeoutError(nodeId, error)
            isNetworkError(msg, error) -> NetworkError(nodeId, error)
            else -> {
                val cause = error.cause
                if (cause != null && cause !== error) matchFromChain(nodeId, cause, depth + 1) else null
            }
        }
    }

    private fun isServerErrorStatus(msg: String?): Boolean =
        msg?.contains("500") == true ||
            msg?.contains("502") == true ||
            msg?.contains("503") == true ||
            msg?.contains("504") == true

    private fun isNetworkError(msg: String?, error: Throwable): Boolean =
        msg?.contains("network", ignoreCase = true) == true ||
            msg?.contains("connection", ignoreCase = true) == true ||
            error is java.net.ConnectException ||
            error is java.net.SocketTimeoutException ||
            error is java.io.IOException
}
