package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AbstractLoadBalancedModelTest {

    class StringModel(val name: String, val shouldFail: Boolean = false) {
        fun call(): String {
            if (shouldFail) error("fail")
            return name
        }
    }

    typealias StringModelNode = DefaultModelNode<StringModel>

    private class TestLoadBalancedModel(
        loadBalancer: LoadBalancer<StringModelNode>,
        maxAttempts: Int = 3,
        errorConverter: ErrorConverter = ErrorConverter.Default,
    ) : AbstractLoadBalancedModel<StringModelNode, StringModel>(loadBalancer, maxAttempts, errorConverter) {
        fun <T> execute(block: (StringModel) -> T): T = executeWithRetry(block)
    }

    @Test
    fun `executeWithRetry should return result on success`() {
        val model = StringModel("test")
        val node = StringModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val lb = object : LoadBalancer<StringModelNode> {
            override val id = "test-lb"
            override val states = listOf(state)
            override fun choose() = state
        }
        val lbModel = TestLoadBalancedModel(lb)

        val result = lbModel.execute { it.call() }

        result.assert().isEqualTo("test")
    }

    @Test
    fun `executeWithRetry should retry on failure`() {
        val failingModel = StringModel("fail", shouldFail = true)
        val successModel = StringModel("success")
        val failNode = StringModelNode("node-1", model = failingModel)
        val successNode = StringModelNode("node-2", model = successModel)
        val failState = DefaultNodeState(failNode)
        val successState = DefaultNodeState(successNode)

        var callCount = 0
        val lb = object : LoadBalancer<StringModelNode> {
            override val id = "test-lb"
            override val states = listOf(failState, successState)
            override fun choose(): NodeState<StringModelNode> {
                callCount++
                return if (callCount == 1) failState else successState
            }
        }
        val lbModel = TestLoadBalancedModel(lb, maxAttempts = 2)

        val result = lbModel.execute { it.call() }

        result.assert().isEqualTo("success")
    }

    @Test
    fun `executeWithRetry should throw AllNodesUnavailableError when retries exhausted`() {
        val failingModel = StringModel("fail")
        val failNode = StringModelNode("node-1", model = failingModel)
        val failState = DefaultNodeState(failNode)

        val lb = object : LoadBalancer<StringModelNode> {
            override val id = "test-lb"
            override val states = listOf(failState)
            override fun choose() = failState
        }
        val lbModel = TestLoadBalancedModel(lb, maxAttempts = 1)

        val ex = assertThrows<AllNodesUnavailableError> {
            lbModel.execute { error("always fails") }
        }
        ex.loadBalancerId.assert().isEqualTo("test-lb")
    }

    @Test
    fun `executeWithRetry should convert error via ErrorConverter`() {
        val model = StringModel("test")
        val node = StringModelNode("node-1", model = model)
        val state = DefaultNodeState(node)
        val customError = RateLimitError("node-1", RuntimeException("429"))
        val converter = ErrorConverter { _, _ -> customError }

        val lb = object : LoadBalancer<StringModelNode> {
            override val id = "test-lb"
            override val states = listOf(state)
            override fun choose() = state
        }
        val lbModel = TestLoadBalancedModel(lb, maxAttempts = 1, errorConverter = converter)

        assertThrows<AllNodesUnavailableError> {
            lbModel.execute { error("fail") }
        }
        state.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
    }

    @Test
    fun `executeWithRetry should call onSuccess on success`() {
        val model = StringModel("test")
        val node = StringModelNode("node-1", model = model)
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 2))
        state.onError(RateLimitError(node.id, null))

        val lb = object : LoadBalancer<StringModelNode> {
            override val id = "test-lb"
            override val states = listOf(state)
            override fun choose() = state
        }
        val lbModel = TestLoadBalancedModel(lb, maxAttempts = 1)

        val result = lbModel.execute { it.call() }
        result.assert().isEqualTo("test")
        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
    }
}
