package me.ahoo.cobal.langchain4j

import me.ahoo.cobal.error.AuthenticationError
import me.ahoo.cobal.error.RateLimitError
import me.ahoo.cobal.error.ServerError
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Instant

class LangChain4jFailurePolicyTest {
    @Test
    fun `RATE_LIMITED should return decision with recoverAt`() {
        val error = RateLimitError("node-1", RuntimeException("429"))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNotNull()
        decision!!.recoverAt.assert().isAfter(Instant.now())
    }

    @Test
    fun `AUTHENTICATION should return long recoverAt`() {
        val error = AuthenticationError("node-1", RuntimeException("401"))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNotNull()
    }

    @Test
    fun `SERVER_ERROR should return null (no state change)`() {
        val error = ServerError("node-1", RuntimeException("500"))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNull()
    }
}
