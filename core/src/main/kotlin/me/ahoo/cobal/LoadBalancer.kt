package me.ahoo.cobal

typealias LoadBalancerId = String

interface LoadBalancer<NODE : Node> {
    val id: LoadBalancerId
    val nodes: List<NODE>
    fun choose(): NodeState<NODE>
}
