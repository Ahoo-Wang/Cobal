package me.ahoo.cobal.langchain4j

import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState

fun <NODE : Node> NODE.toNodeState(
    circuitOpenThreshold: Int = 5,
): DefaultNodeState<NODE> = DefaultNodeState(
    this,
    failurePolicy = LangChain4jFailurePolicy,
    circuitOpenThreshold = circuitOpenThreshold
)

fun <NODE : Node> List<NODE>.toNodeStates(
    circuitOpenThreshold: Int = 5,
): List<NodeState<NODE>> = map { it.toNodeState(circuitOpenThreshold) }
