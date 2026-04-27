package me.ahoo.cobal.langchain4j

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
 * LangChain4j-specific [NodeErrorConverter] that maps framework exceptions to the [NodeError] hierarchy.
 *
 * Mapping rules:
 * - [RateLimitException] → [RateLimitError] (retriable)
 * - [InvalidRequestException] → [InvalidRequestError] (not retriable)
 * - [AuthenticationException] → [AuthenticationError] (not retriable)
 * - [TimeoutException] → [TimeoutError] (retriable)
 * - [InternalServerException] → [ServerError] (retriable)
 * - [HttpException] with 5xx → [ServerError]; 4xx → [InvalidRequestError]
 * - [IOException] → [NetworkError] (retriable)
 * - Unknown exceptions: unwraps one level of [cause][Throwable.cause], then falls back to generic [NodeError]
 */
object LangChain4JNodeErrorConverter : NodeErrorConverter {
    override fun convert(nodeId: NodeId, error: Throwable): NodeError {
        return when (error) {
            is dev.langchain4j.exception.RateLimitException -> RateLimitError(nodeId, error)
            is dev.langchain4j.exception.InvalidRequestException -> InvalidRequestError(nodeId, error)
            is dev.langchain4j.exception.AuthenticationException -> AuthenticationError(nodeId, error)
            is dev.langchain4j.exception.TimeoutException -> TimeoutError(nodeId, error)
            is dev.langchain4j.exception.InternalServerException -> ServerError(nodeId, error)
            is dev.langchain4j.exception.HttpException -> {
                if (error.statusCode() >= 500) {
                    ServerError(nodeId, error)
                } else {
                    InvalidRequestError(nodeId, error)
                }
            }
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
