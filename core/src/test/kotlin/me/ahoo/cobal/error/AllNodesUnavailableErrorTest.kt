package me.ahoo.cobal.error

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class AllNodesUnavailableErrorTest {
    @Test
    fun `AllNodesUnavailableError contains loadBalancerId`() {
        val error = AllNodesUnavailableError("lb-1")
        error.loadBalancerId.assert().isEqualTo("lb-1")
        error.message.assert().isEqualTo("All nodes unavailable in load balancer: lb-1")
    }
}
