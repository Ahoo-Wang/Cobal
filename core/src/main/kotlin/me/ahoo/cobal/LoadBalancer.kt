package me.ahoo.cobal

typealias LoadBalancerId = String

interface LoadBalancer<NODE : Node> {
    val id: LoadBalancerId
    val states: List<NodeState<NODE>>
    val availableStates: List<NodeState<NODE>>
        get() = states.filter { it.available }
    fun choose(): NodeState<NODE>
}
