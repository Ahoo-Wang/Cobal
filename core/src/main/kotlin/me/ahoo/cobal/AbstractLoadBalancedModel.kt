package me.ahoo.cobal

abstract class AbstractLoadBalancedModel<NODE : ModelNode<MODEL>, MODEL>(
    val loadBalancer: LoadBalancer<NODE>,
    val maxRetries: Int = 3,
    private val errorConverter: ErrorConverter
) {

    @Suppress("TooGenericExceptionCaught")
    protected fun <T> executeWithRetry(block: (MODEL) -> T): T {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return block(selected.node.model)
            } catch (e: Throwable) {
                val nodeError = errorConverter.convert(selected.node.id, e)
                    ?: ServerError(selected.node.id, e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }
}
