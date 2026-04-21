package me.ahoo.cobal.langchain4j

import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.time.Clock

fun <NODE : Node> NODE.toNodeState(
    circuitOpenThreshold: Int = 5,
    clock: Clock = Clock.systemUTC()
): DefaultNodeState<NODE> = DefaultNodeState(this, LangChain4jFailurePolicy, circuitOpenThreshold, clock)

fun <NODE : Node> List<NODE>.toNodeStates(
    circuitOpenThreshold: Int = 5,
    clock: Clock = Clock.systemUTC()
): List<NodeState<NODE>> = map { it.toNodeState(circuitOpenThreshold, clock) }
