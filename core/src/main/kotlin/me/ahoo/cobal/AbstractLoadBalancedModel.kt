package me.ahoo.cobal

abstract class AbstractLoadBalancedModel<NODE : ModelNode<MODEL>, MODEL>(
    val loadBalancer: LoadBalancer<NODE>,
    protected val errorConverter: ErrorConverter,
) {
    protected open val maxAttempts: Int = loadBalancer.availableStates.size

    @Suppress("TooGenericExceptionCaught")
    protected fun <T : Any> executeWithRetry(block: (MODEL) -> T): T {
        repeat(maxAttempts) {
            val selected = loadBalancer.choose()
            val acquired = selected.tryAcquirePermission()
            if (!acquired) {
                return@repeat
            }
            val start = selected.currentTimestamp
            try {
                val result = block(selected.node.model)
                val duration = selected.currentTimestamp - start
                selected.onResult(duration, selected.timestampUnit, result)
                return result
            } catch (e: Exception) {
                val nodeError = errorConverter.convert(selected.node.id, e)
                val duration = selected.currentTimestamp - start
                selected.onError(duration, selected.timestampUnit, nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }
}
