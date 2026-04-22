package me.ahoo.cobal.langchain4j

import me.ahoo.cobal.Node
import me.ahoo.cobal.state.DefaultCircuitBreaker
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.cobal.state.NodeState
import java.time.Duration

fun <NODE : Node> NODE.toNodeState(
    circuitOpenThreshold: Int = 5,
    circuitBreakerRecoveryDuration: Duration = Duration.ofSeconds(60),
): DefaultNodeState<NODE> = DefaultNodeState(
    this,
    failurePolicy = LangChain4jFailurePolicy,
    circuitBreaker = DefaultCircuitBreaker(circuitOpenThreshold, circuitBreakerRecoveryDuration),
)

fun <NODE : Node> List<NODE>.toNodeStates(
    circuitOpenThreshold: Int = 5,
    circuitBreakerRecoveryDuration: Duration = Duration.ofSeconds(60),
): List<NodeState<NODE>> = map { it.toNodeState(circuitOpenThreshold, circuitBreakerRecoveryDuration) }
