package me.ahoo.cobal.springai

import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.error.NodeErrorConverter
import me.ahoo.cobal.state.NodeState
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes a streaming [block] against selected nodes' models with automatic retry on failure.
 *
 * Uses emission tracking — if any data has been emitted to the subscriber,
 * errors are passed through without retry.
 *
 * On each attempt: acquires circuit breaker permission via [NodeState.tryAcquirePermission],
 * creates a Flux via [block], records success/failure with timing.
 * Skips nodes whose circuit breaker denies permission.
 * [InvalidRequestError] is thrown immediately without retry.
 *
 * @param NODE the model node type
 * @param MODEL the framework-specific model type
 * @param R the result type
 * @param nodeErrorConverter translates framework exceptions to [me.ahoo.cobal.error.NodeError]
 * @param maxAttempts maximum number of retry attempts, defaults to the number of available nodes
 * @param block the streaming operation to execute against the selected node's model
 * @throws AllNodesUnavailableError if all attempts are exhausted before any emission
 * @throws InvalidRequestError immediately on bad request, without retry
 */
fun <NODE : ModelNode<MODEL>, MODEL, R : Any> LoadBalancer<NODE>.streamExecute(
    nodeErrorConverter: NodeErrorConverter,
    maxAttempts: Int = availableStates.size,
    block: (MODEL) -> Flux<R>,
): Flux<R> {
    return StreamExecuteHelper(this, nodeErrorConverter, block).execute(maxAttempts)
}

private class StreamExecuteHelper<NODE : ModelNode<MODEL>, MODEL, R : Any>(
    private val loadBalancer: LoadBalancer<NODE>,
    private val nodeErrorConverter: NodeErrorConverter,
    private val block: (MODEL) -> Flux<R>,
) {
    @Suppress("ReturnCount")
    fun execute(remainingRetries: Int): Flux<R> {
        if (remainingRetries <= 0) {
            return Flux.error(AllNodesUnavailableError(loadBalancer.id))
        }

        val candidate = loadBalancer.choose()
        val acquired = candidate.tryAcquirePermission()
        if (!acquired) {
            return execute(remainingRetries - 1)
        }

        val start = candidate.currentTimestamp
        val emitted = AtomicBoolean(false)

        return block(candidate.node.model)
            .doOnNext { emitted.set(true) }
            .doOnComplete {
                val duration = candidate.currentTimestamp - start
                candidate.onResult(duration, candidate.timestampUnit, Unit)
            }
            .onErrorResume { error ->
                if (emitted.get()) {
                    Flux.error(error)
                } else {
                    val nodeError = nodeErrorConverter.convert(candidate.node.id, error)
                    val duration = candidate.currentTimestamp - start
                    candidate.onError(duration, candidate.timestampUnit, nodeError)
                    if (nodeError is InvalidRequestError) {
                        Flux.error(nodeError)
                    } else {
                        execute(remainingRetries - 1)
                    }
                }
            }
    }
}
