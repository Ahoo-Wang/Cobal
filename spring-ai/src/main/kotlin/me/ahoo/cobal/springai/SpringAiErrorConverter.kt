package me.ahoo.cobal.springai

import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.ErrorConverter
import me.ahoo.cobal.InvalidRequestError
import me.ahoo.cobal.RateLimitError

val SpringAiErrorConverter = ErrorConverter { nodeId, error ->
    when {
        error.message?.contains("429") == true -> RateLimitError(nodeId, error)
        error.message?.contains("401") == true || error.message?.contains("403") == true -> AuthenticationError(
            nodeId,
            error
        )
        error.message?.contains("400") == true -> InvalidRequestError(nodeId, error)
        else -> null
    }
}
