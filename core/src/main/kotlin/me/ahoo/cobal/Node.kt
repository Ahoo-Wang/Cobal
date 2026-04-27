package me.ahoo.cobal

import me.ahoo.cobal.state.AvailableCapable

/** Unique identifier for a [Node] in the load balancer. */
typealias NodeId = String

/**
 * Represents an endpoint in the load balancer.
 *
 * Each node has a unique [id] and a [weight] that influences selection probability
 * in weighted algorithms. Nodes with `weight <= 0` are excluded from selection.
 */
interface Node : AvailableCapable {
    val id: NodeId

    /** Selection weight. Higher values mean more traffic. Defaults to 1. */
    val weight: Int
        get() = 1

    override val available: Boolean
        get() = weight > 0
}

/** Basic [Node] implementation. */
data class DefaultNode(
    override val id: NodeId,
    override val weight: Int = 1,
) : Node

/**
 * A [Node] that wraps a framework-specific model instance.
 *
 * Used by [LoadBalancer.execute] to delegate requests to the underlying model.
 */
interface ModelNode<MODEL> : Node {
    val model: MODEL
}

/** Concrete [ModelNode] holding a framework-specific model. */
class DefaultModelNode<MODEL>(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: MODEL,
) : ModelNode<MODEL>
