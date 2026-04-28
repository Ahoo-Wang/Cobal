package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.moderation.ModerationModel
import dev.langchain4j.model.moderation.ModerationRequest
import dev.langchain4j.model.moderation.ModerationResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class LoadBalancedModerationModelTest {

    @Test
    fun `doModerate should delegate to underlying model`() {
        val model = mockk<ModerationModel>()
        val request = ModerationRequest.builder().texts(listOf("hello")).build()
        val expectedResponse = mockk<ModerationResponse>()

        every { model.doModerate(any<ModerationRequest>()) } returns expectedResponse

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedModerationModel(lb)

        val result = balancedModel.doModerate(request)
        result.assert().isEqualTo(expectedResponse)
        verify { model.doModerate(any<ModerationRequest>()) }
    }

    @Test
    fun `doModerate should throw AllNodesUnavailableError when all models fail`() {
        val model = mockk<ModerationModel>()
        val request = ModerationRequest.builder().texts(listOf("hello")).build()

        every { model.doModerate(any<ModerationRequest>()) } throws RuntimeException("fail")

        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = RandomLoadBalancer("test-lb", listOf(state))
        val balancedModel = LoadBalancedModerationModel(lb)

        assertThrownBy<AllNodesUnavailableError> {
            balancedModel.doModerate(request)
        }
    }
}
