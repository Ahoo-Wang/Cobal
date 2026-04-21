package me.ahoo.cobal.langchain4j

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

object LangChain4jErrorConverter : ErrorConverter {
    override fun convert(nodeId: NodeId, error: Throwable): CobalError {
        return when (error) {
            is dev.langchain4j.exception.RateLimitException -> RateLimitError(nodeId, error)
            is dev.langchain4j.exception.InvalidRequestException -> InvalidRequestError(nodeId, error)
            is dev.langchain4j.exception.AuthenticationException -> AuthenticationError(nodeId, error)
            is dev.langchain4j.exception.TimeoutException -> TimeoutError(nodeId, error)
            is dev.langchain4j.exception.InternalServerException -> ServerError(nodeId, error)
            is dev.langchain4j.exception.HttpException -> ServerError(nodeId, error)
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.io.IOException -> NetworkError(nodeId, error)
            else -> {
                val cause = error.cause
                if (cause != null && cause !== error) {
                    return convert(nodeId, cause)
                }
                NodeError(nodeId, error.message, error)
            }
        }
    }
}
