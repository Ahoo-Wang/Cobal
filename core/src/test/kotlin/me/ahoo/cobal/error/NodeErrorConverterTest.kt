package me.ahoo.cobal.error

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class NodeErrorConverterTest {

    @Test
    fun `Default converter should return NodeError`() {
        val error = RuntimeException("test")
        val result = NodeErrorConverter.Default.convert("node-1", error)

        result.assert().isInstanceOf(NodeError::class.java)
        result.nodeId.assert().isEqualTo("node-1")
    }

    @Test
    fun `Default converter should preserve cause message`() {
        val error = RuntimeException("original message")
        val result = NodeErrorConverter.Default.convert("node-1", error)
        result.message.assert().isEqualTo("original message")
        result.cause.assert().isEqualTo(error)
    }

    @Test
    fun `Custom converter should convert to specific error type`() {
        val converter = NodeErrorConverter { nodeId, _ -> RateLimitError(nodeId, null) }
        val result = converter.convert("node-1", RuntimeException("429"))
        result.assert().isInstanceOf(RateLimitError::class.java)
        result.assert().isInstanceOf(RetriableError::class.java)
        (result as RateLimitError).nodeId.assert().isEqualTo("node-1")
    }
}
