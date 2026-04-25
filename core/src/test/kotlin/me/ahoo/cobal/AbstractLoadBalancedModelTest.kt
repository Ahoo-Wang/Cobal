package me.ahoo.cobal

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.NodeError
import me.ahoo.cobal.error.NodeErrorConverter
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.cobal.state.NodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration

class TestModel(val name: String, val shouldFail: Boolean = false) {
    fun call(): String {
        if (shouldFail) error("fail")
        return name
    }
}

private class TestLoadBalancedModel(
    loadBalancer: LoadBalancer<DefaultModelNode<TestModel>>,
    maxAttempts: Int = 3,
    nodeErrorConverter: NodeErrorConverter = NodeErrorConverter.Default,
) : AbstractLoadBalancedModel<DefaultModelNode<TestModel>, TestModel>(loadBalancer, nodeErrorConverter) {
    override val maxAttempts: Int = maxAttempts
    fun <T : Any> execute(block: (TestModel) -> T): T = executeWithRetry { model -> block(model) }
}

class AbstractLoadBalancedModelTest {

    companion object {
        private fun strictCircuitBreakerConfig() = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build()
    }

    @Test
    fun `executeWithRetry should return result on success`() {
        val model = TestModel("success")
        val node = DefaultModelNode("node-1", model = model)
        val state = DefaultNodeState(node)

        val lb = object : LoadBalancer<DefaultModelNode<TestModel>> {
            override val id: LoadBalancerId = "test-lb"
            override val states = listOf(state)
            override fun choose(): NodeState<DefaultModelNode<TestModel>> = state
        }

        val lbModel = TestLoadBalancedModel(lb)
        val result = lbModel.execute { it.call() }
        result.assert().isEqualTo("success")
    }

    @Test
    fun `executeWithRetry should retry on failure`() {
        val failModel = TestModel("fail", shouldFail = true)
        val successModel = TestModel("success")
        val failNode = DefaultModelNode("node-fail", model = failModel)
        val successNode = DefaultModelNode("node-success", model = successModel)
        val failState = DefaultNodeState(failNode)
        val successState = DefaultNodeState(successNode)

        var callCount = 0
        val lb = object : LoadBalancer<DefaultModelNode<TestModel>> {
            override val id: LoadBalancerId = "test-lb"
            override val states = listOf(failState, successState)
            override fun choose(): NodeState<DefaultModelNode<TestModel>> =
                if (callCount++ == 0) failState else successState
        }

        val lbModel = TestLoadBalancedModel(lb, maxAttempts = 3)
        val result = lbModel.execute { it.call() }
        result.assert().isEqualTo("success")
        callCount.assert().isEqualTo(2)
    }

    @Test
    fun `executeWithRetry should throw AllNodesUnavailableError when retries exhausted`() {
        val failModel = TestModel("fail", shouldFail = true)
        val failNode = DefaultModelNode("node-fail", model = failModel)
        val failState = DefaultNodeState(failNode)

        val lb = object : LoadBalancer<DefaultModelNode<TestModel>> {
            override val id: LoadBalancerId = "test-lb"
            override val states = listOf(failState)
            override fun choose(): NodeState<DefaultModelNode<TestModel>> = failState
        }

        val lbModel = TestLoadBalancedModel(lb, maxAttempts = 1)

        var caught: AllNodesUnavailableError? = null
        try {
            lbModel.execute { it.call() }
        } catch (e: AllNodesUnavailableError) {
            caught = e
        }
        caught.assert().isNotNull()
        caught!!.loadBalancerId.assert().isEqualTo("test-lb")
    }

    @Test
    fun `executeWithRetry should skip node when permission not acquired`() {
        val openModel = TestModel("open-node")
        val successModel = TestModel("success")
        val openNode = DefaultModelNode("node-open", model = openModel)
        val successNode = DefaultModelNode("node-success", model = successModel)

        val openState = DefaultNodeState(openNode, CircuitBreaker.of("node-open", strictCircuitBreakerConfig()))
        openState.circuitBreaker.onError(0, openState.circuitBreaker.timestampUnit, RuntimeException("error"))

        val successState = DefaultNodeState(successNode)

        var callCount = 0
        val lb = object : LoadBalancer<DefaultModelNode<TestModel>> {
            override val id: LoadBalancerId = "test-lb"
            override val states = listOf(openState, successState)
            override fun choose(): NodeState<DefaultModelNode<TestModel>> =
                if (callCount++ == 0) openState else successState
        }

        val lbModel = TestLoadBalancedModel(lb, maxAttempts = 3)
        val result = lbModel.execute { it.call() }
        result.assert().isEqualTo("success")
    }

    @Test
    fun `executeWithRetry should convert error via ErrorConverter`() {
        var converterCalled = false
        var capturedNodeId: String? = null
        val customConverter = NodeErrorConverter { nodeId, _ ->
            converterCalled = true
            capturedNodeId = nodeId
            NodeError(nodeId, "custom error", null)
        }

        val failModel = TestModel("fail", shouldFail = true)
        val failNode = DefaultModelNode("node-fail", model = failModel)
        val failState = DefaultNodeState(failNode)
        val successModel = TestModel("success")
        val successNode = DefaultModelNode("node-success", model = successModel)
        val successState = DefaultNodeState(successNode)

        var callCount = 0
        val lb = object : LoadBalancer<DefaultModelNode<TestModel>> {
            override val id: LoadBalancerId = "test-lb"
            override val states = listOf(failState, successState)
            override fun choose(): NodeState<DefaultModelNode<TestModel>> =
                if (callCount++ == 0) failState else successState
        }

        val lbModel = TestLoadBalancedModel(lb, maxAttempts = 3, nodeErrorConverter = customConverter)
        val result = lbModel.execute { it.call() }
        result.assert().isEqualTo("success")
        converterCalled.assert().isTrue()
        capturedNodeId.assert().isEqualTo("node-fail")
    }
}
