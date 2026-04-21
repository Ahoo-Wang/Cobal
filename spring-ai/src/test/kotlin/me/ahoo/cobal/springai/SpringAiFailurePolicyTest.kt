package me.ahoo.cobal.springai

import me.ahoo.cobal.AuthenticationError
import me.ahoo.cobal.RateLimitError
import me.ahoo.cobal.ServerError
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Instant

class SpringAiFailurePolicyTest {
    @Test
    fun `RATE_LIMITED should return decision with recoverAt`() {
        val error = RateLimitError("node-1", RuntimeException("429"))
        val decision = SpringAiFailurePolicy.evaluate(error)
        decision.assert().isNotNull()
        decision!!.recoverAt.assert().isAfter(Instant.now())
    }

    @Test
    fun `AUTHENTICATION should return decision`() {
        val error = AuthenticationError("node-1", RuntimeException("401"))
        val decision = SpringAiFailurePolicy.evaluate(error)
        decision.assert().isNotNull()
    }

    @Test
    fun `SERVER_ERROR should return null (no state change)`() {
        val error = ServerError("node-1", RuntimeException("500"))
        val decision = SpringAiFailurePolicy.evaluate(error)
        decision.assert().isNull()
    }
}
