package me.ahoo.cobal.error

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class CobalErrorTest {

    @Test
    fun `CobalError should extend RuntimeException`() {
        val cause = RuntimeException("root")
        val error = object : CobalError("test message", cause) {}
        error.assert().isInstanceOf(RuntimeException::class.java)
        error.message.assert().isEqualTo("test message")
        error.cause.assert().isEqualTo(cause)
    }
}
