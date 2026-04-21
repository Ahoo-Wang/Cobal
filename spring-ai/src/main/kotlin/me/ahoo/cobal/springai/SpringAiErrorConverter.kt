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
        return when {
            error.message?.contains("429") == true -> RateLimitError(nodeId, error)
            error.message?.contains("401") == true || error.message?.contains("403") == true -> AuthenticationError(
                nodeId,
                error
            )

            error.message?.contains("400") == true -> InvalidRequestError(nodeId, error)
            error.message?.contains("500") == true ||
                error.message?.contains("502") == true ||
                error.message?.contains("503") == true ||
                error.message?.contains("504") == true -> ServerError(nodeId, error)
            error.message?.contains("timeout", ignoreCase = true) == true -> TimeoutError(nodeId, error)
            error.message?.contains("network", ignoreCase = true) == true ||
                error.message?.contains("connection", ignoreCase = true) == true -> NetworkError(nodeId, error)
            else -> NodeError(nodeId, error.message, error)
        }
    }
}
