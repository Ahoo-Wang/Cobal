package me.ahoo.cobal.dsl

import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.algorithm.RoundRobinLoadBalancer
import me.ahoo.cobal.algorithm.WeightedRandomLoadBalancer
import me.ahoo.cobal.algorithm.WeightedRoundRobinLoadBalancer
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

private data class TestModel(val name: String)

class LoadBalancerDslTest {

    @Test
    fun `loadBalancer should default to weightedRoundRobin when no algorithm specified`() {
        val lb = loadBalancer<TestModel>("test-lb") {
            node("n1") { model(TestModel("m1")) }
            node("n2") { model(TestModel("m2")) }
        }

        lb.assert().isInstanceOf(WeightedRoundRobinLoadBalancer::class.java)
        lb.id.assert().isEqualTo("test-lb")
        lb.states.assert().hasSize(2)
        lb.states[0].node.id.assert().isEqualTo("n1")
        lb.states[0].node.model.assert().isEqualTo(TestModel("m1"))
        lb.states[1].node.id.assert().isEqualTo("n2")
        lb.states[1].node.model.assert().isEqualTo(TestModel("m2"))
    }

    @Test
    fun `loadBalancer with roundRobin should create RoundRobinLoadBalancer`() {
        val lb = loadBalancer<TestModel>("rr") {
            roundRobin()
            node("n1") { model(TestModel("m1")) }
        }
        lb.assert().isInstanceOf(RoundRobinLoadBalancer::class.java)
    }

    @Test
    fun `loadBalancer with random should create RandomLoadBalancer`() {
        val lb = loadBalancer<TestModel>("rand") {
            random()
            node("n1") { model(TestModel("m1")) }
        }
        lb.assert().isInstanceOf(RandomLoadBalancer::class.java)
    }

    @Test
    fun `loadBalancer with weightedRoundRobin should create WeightedRoundRobinLoadBalancer`() {
        val lb = loadBalancer<TestModel>("wrr") {
            weightedRoundRobin()
            node("n1") { model(TestModel("m1")) }
        }
        lb.assert().isInstanceOf(WeightedRoundRobinLoadBalancer::class.java)
    }

    @Test
    fun `loadBalancer with weightedRandom should create WeightedRandomLoadBalancer`() {
        val lb = loadBalancer<TestModel>("wr") {
            weightedRandom()
            node("n1") { model(TestModel("m1")) }
        }
        lb.assert().isInstanceOf(WeightedRandomLoadBalancer::class.java)
    }

    @Test
    fun `node should accept custom weight`() {
        val lb = loadBalancer<TestModel>("wlb") {
            roundRobin()
            node("n1", weight = 5) { model(TestModel("m1")) }
        }
        lb.states[0].node.weight.assert().isEqualTo(5)
    }

    @Test
    fun `node circuitBreaker should apply custom config`() {
        val lb = loadBalancer<TestModel>("cb-lb") {
            roundRobin()
            node("n1") {
                model(TestModel("m1"))
                circuitBreaker {
                    failureRateThreshold(50f)
                    slidingWindowSize(10)
                    waitDurationInOpenState(Duration.ofSeconds(30))
                }
            }
        }

        val state = lb.states[0]
        state.circuitBreaker.circuitBreakerConfig.failureRateThreshold.assert().isEqualTo(50.0f)
        state.circuitBreaker.circuitBreakerConfig.slidingWindowSize.assert().isEqualTo(10)
    }

    @Test
    fun `calling algorithm function multiple times should override previous`() {
        val lb = loadBalancer<TestModel>("override") {
            roundRobin()
            random()
            node("n1") { model(TestModel("m1")) }
        }
        lb.assert().isInstanceOf(RandomLoadBalancer::class.java)
    }

    @Test
    fun `calling model multiple times should override previous`() {
        val lb = loadBalancer<TestModel>("override-model") {
            roundRobin()
            node("n1") {
                model(TestModel("m1"))
                model(TestModel("m2"))
            }
        }
        lb.states[0].node.model.assert().isEqualTo(TestModel("m2"))
    }

    @Test
    fun `calling model zero times should throw IllegalStateException`() {
        assertThrownBy<IllegalStateException> {
            loadBalancer<TestModel>("bad") {
                roundRobin()
                node("n1") { }
            }
        }
    }

    @Test
    fun `empty node list should throw IllegalArgumentException`() {
        assertThrownBy<IllegalArgumentException> {
            loadBalancer<TestModel>("empty") {
                roundRobin()
            }
        }
    }

    @Test
    fun `loadBalancer choose should delegate to underlying algorithm`() {
        val lb = loadBalancer<TestModel>("choose-lb") {
            roundRobin()
            node("n1") { model(TestModel("m1")) }
            node("n2") { model(TestModel("m2")) }
        }

        lb.choose().node.id.assert().isEqualTo("n1")
        lb.choose().node.id.assert().isEqualTo("n2")
        lb.choose().node.id.assert().isEqualTo("n1")
    }
}
