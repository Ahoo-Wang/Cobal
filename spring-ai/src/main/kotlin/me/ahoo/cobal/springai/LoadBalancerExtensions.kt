package me.ahoo.cobal.springai

import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.error.AllNodesUnavailableError
import me.ahoo.cobal.error.InvalidRequestError
import me.ahoo.cobal.error.NodeError
import me.ahoo.cobal.error.NodeErrorConverter
import me.ahoo.cobal.state.NodeState
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes a streaming [block] against selected nodes' models with automatic retry on failure.
 *
 * The returned [Flux] is cold: node selection, permission acquisition, and the [maxAttempts]
 * default are all deferred until subscription time, so the LB state observed reflects the
 * subscriber's perspective — not the moment this extension is invoked.
 *
 * Uses emission tracking — if any data has been emitted to the subscriber,
 * errors are passed through without retry.
 *
 * On each attempt: acquires circuit breaker permission via [NodeState.tryAcquirePermission],
 * creates a Flux via [block], records success/failure with timing.
 * Permission denials do **not** consume an attempt; they are bounded separately by node count.
 * [InvalidRequestError] is thrown immediately without retry.
 *
 * @param NODE the model node type
 * @param MODEL the framework-specific model type
 * @param R the result type
 * @param nodeErrorConverter translates framework exceptions to [NodeError]
 * @param maxAttempts maximum number of [block] invocations. `0` (default) auto-sizes to the
 *   number of available nodes at subscription time. Must be `>= 0`.
 * @param block the streaming operation to execute against the selected node's model
 * @throws AllNodesUnavailableError if all attempts are exhausted before any emission; per-attempt
 *   [NodeError]s are attached via [Throwable.addSuppressed].
 * @throws InvalidRequestError immediately on bad request, without retry
 */
fun <NODE : ModelNode<MODEL>, MODEL, R : Any> LoadBalancer<NODE>.streamExecute(
    nodeErrorConverter: NodeErrorConverter,
    maxAttempts: Int = 0,
    block: (MODEL) -> Flux<R>,
): Flux<R> {
    require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
    return Flux.defer {
        val effectiveMax = if (maxAttempts > 0) maxAttempts else availableStates.size.coerceAtLeast(1)
        StreamExecuteHelper(this, nodeErrorConverter, block).execute(effectiveMax, states.size, mutableListOf())
    }
}

private class StreamExecuteHelper<NODE : ModelNode<MODEL>, MODEL, R : Any>(
    private val loadBalancer: LoadBalancer<NODE>,
    private val nodeErrorConverter: NodeErrorConverter,
    private val block: (MODEL) -> Flux<R>,
) {
    @Suppress("ReturnCount")
    fun execute(remainingAttempts: Int, rejectionBudget: Int, failures: MutableList<NodeError>): Flux<R> {
        if (remainingAttempts <= 0 || loadBalancer.availableStates.isEmpty()) {
            return Flux.error(AllNodesUnavailableError(loadBalancer.id, failures))
        }

        val candidate = try {
            loadBalancer.choose()
        } catch (e: AllNodesUnavailableError) {
            return Flux.error(if (failures.isEmpty()) e else AllNodesUnavailableError(loadBalancer.id, failures))
        }

        if (!candidate.tryAcquirePermission()) {
            val nextBudget = rejectionBudget - 1
            if (nextBudget <= 0) return Flux.error(AllNodesUnavailableError(loadBalancer.id, failures))
            return execute(remainingAttempts, nextBudget, failures)
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
                    failures.add(nodeError)
                    if (nodeError is InvalidRequestError) {
                        Flux.error(nodeError)
                    } else {
                        execute(remainingAttempts - 1, rejectionBudget, failures)
                    }
                }
            }
    }
}
