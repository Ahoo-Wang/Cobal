package me.ahoo.cobal.dsl

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.algorithm.RandomLoadBalancer
import me.ahoo.cobal.algorithm.RoundRobinLoadBalancer
import me.ahoo.cobal.algorithm.WeightedRandomLoadBalancer
import me.ahoo.cobal.algorithm.WeightedRoundRobinLoadBalancer
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.cobal.state.NodeState

private typealias AlgorithmFactory<MODEL> =
    (LoadBalancerId, List<NodeState<ModelNode<MODEL>>>) -> LoadBalancer<ModelNode<MODEL>>

@CobalDsl
class LoadBalancerBuilder<MODEL : Any> {

    private var algorithmFactory: AlgorithmFactory<MODEL> = { id, states -> WeightedRoundRobinLoadBalancer(id, states) }
    private val nodeBuilders = mutableListOf<NodeBuilder<MODEL>>()

    fun roundRobin() {
        algorithmFactory = { id, states -> RoundRobinLoadBalancer(id, states) }
    }

    fun random() {
        algorithmFactory = { id, states -> RandomLoadBalancer(id, states) }
    }

    fun weightedRoundRobin() {
        algorithmFactory = { id, states -> WeightedRoundRobinLoadBalancer(id, states) }
    }

    fun weightedRandom() {
        algorithmFactory = { id, states -> WeightedRandomLoadBalancer(id, states) }
    }

    fun node(id: String, weight: Int = 1, block: NodeBuilder<MODEL>.() -> Unit) {
        nodeBuilders.add(NodeBuilder<MODEL>(id, weight).apply(block))
    }

    internal fun build(id: LoadBalancerId): LoadBalancer<ModelNode<MODEL>> {
        require(nodeBuilders.isNotEmpty()) { "At least one node must be added." }

        val states: List<NodeState<ModelNode<MODEL>>> = nodeBuilders.map { builder ->
            val (modelNode, circuitBreakerConfig) = builder.build()
            val circuitBreaker = CircuitBreaker.of(modelNode.id, circuitBreakerConfig)
            DefaultNodeState(modelNode, circuitBreaker)
        }

        return algorithmFactory(id, states)
    }
}

fun <MODEL : Any> loadBalancer(
    id: LoadBalancerId,
    block: LoadBalancerBuilder<MODEL>.() -> Unit,
): LoadBalancer<ModelNode<MODEL>> =
    LoadBalancerBuilder<MODEL>().apply(block).build(id)
