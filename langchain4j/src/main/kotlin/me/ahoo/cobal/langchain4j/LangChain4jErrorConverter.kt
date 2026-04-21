package me.ahoo.cobal.langchain4j

import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.ErrorConverter
import me.ahoo.cobal.InvalidRequestError
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.TimeoutError

val LangChain4jErrorConverter = ErrorConverter { nodeId, error ->
    when (error) {
        is dev.langchain4j.exception.RateLimitException -> RateLimitError(nodeId, error)
        is dev.langchain4j.exception.InvalidRequestException -> InvalidRequestError(nodeId, error)
        is dev.langchain4j.exception.AuthenticationException -> AuthenticationError(nodeId, error)
        is dev.langchain4j.exception.TimeoutException -> TimeoutError(nodeId, error)
        else -> null
    }
}
