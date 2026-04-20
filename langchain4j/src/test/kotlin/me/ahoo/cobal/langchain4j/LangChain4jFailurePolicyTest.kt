package me.ahoo.cobal.langchain4j

import me.ahoo.cobal.ErrorCategory
import me.ahoo.cobal.NodeError
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Instant

class LangChain4jFailurePolicyTest {
    @Test
    fun `RATE_LIMITED should return decision with recoverAt`() {
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNotNull()
        decision!!.recoverAt.assert().isAfter(Instant.now())
    }

    @Test
    fun `AUTHENTICATION should return long recoverAt`() {
        val error = NodeError(ErrorCategory.AUTHENTICATION, RuntimeException("401"))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNotNull()
    }

    @Test
    fun `SERVER_ERROR should return null (no state change)`() {
        val error = NodeError(ErrorCategory.SERVER_ERROR, RuntimeException("500"))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNull()
    }
}
