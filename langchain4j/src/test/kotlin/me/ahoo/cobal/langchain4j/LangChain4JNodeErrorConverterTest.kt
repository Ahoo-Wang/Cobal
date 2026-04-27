package me.ahoo.cobal.langchain4j

import dev.langchain4j.exception.AuthenticationException
import dev.langchain4j.exception.HttpException
import dev.langchain4j.exception.InternalServerException
import dev.langchain4j.exception.InvalidRequestException
import dev.langchain4j.exception.RateLimitException
import dev.langchain4j.exception.TimeoutException
import me.ahoo.cobal.error.AuthenticationError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.error.NetworkError
import me.ahoo.cobal.error.NodeError
import me.ahoo.cobal.error.RateLimitError
import me.ahoo.cobal.error.ServerError
import me.ahoo.cobal.error.TimeoutError
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException

class LangChain4JNodeErrorConverterTest {

    @Test
    fun `convert RateLimitException to RateLimitError`() {
        val error = RateLimitException("rate limited")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(RateLimitError::class.java)
        nodeError.nodeId.assert().isEqualTo("node-1")
        nodeError.cause.assert().isEqualTo(error)
    }

    @Test
    fun `convert InvalidRequestException to InvalidRequestError`() {
        val error = InvalidRequestException("bad request")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(InvalidRequestError::class.java)
        nodeError.nodeId.assert().isEqualTo("node-1")
    }

    @Test
    fun `convert AuthenticationException to AuthenticationError`() {
        val error = AuthenticationException("auth failed")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(AuthenticationError::class.java)
        nodeError.nodeId.assert().isEqualTo("node-1")
    }

    @Test
    fun `convert TimeoutException to TimeoutError`() {
        val error = TimeoutException("timed out")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(TimeoutError::class.java)
        nodeError.nodeId.assert().isEqualTo("node-1")
    }

    @Test
    fun `convert InternalServerException to ServerError`() {
        val error = InternalServerException("internal error")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(ServerError::class.java)
        nodeError.nodeId.assert().isEqualTo("node-1")
    }

    @Test
    fun `convert HttpException to ServerError`() {
        val error = HttpException(500, "server error")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(ServerError::class.java)
    }

    @Test
    fun `convert ConnectException to NetworkError`() {
        val error = ConnectException("connection refused")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(NetworkError::class.java)
    }

    @Test
    fun `convert SocketTimeoutException to NetworkError`() {
        val error = SocketTimeoutException("read timed out")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(NetworkError::class.java)
    }

    @Test
    fun `convert IOException to NetworkError`() {
        val error = IOException("I/O error")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(NetworkError::class.java)
    }

    @Test
    fun `convert wrapped cause recursively`() {
        val cause = RateLimitException("rate limited")
        val wrapped = ExecutionException(cause)
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", wrapped)
        nodeError.assert().isInstanceOf(RateLimitError::class.java)
        nodeError.cause.assert().isEqualTo(cause)
    }

    @Test
    fun `convert unknown error to generic NodeError`() {
        val error = IllegalStateException("something went wrong")
        val nodeError = LangChain4JNodeErrorConverter.convert("node-1", error)
        nodeError.assert().isInstanceOf(NodeError::class.java)
        nodeError.assert().isNotInstanceOf(RateLimitError::class.java)
    }
}
