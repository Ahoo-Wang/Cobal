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
                val result = block(selected.node.model)
                selected.onSuccess()
                return result
            } catch (e: Exception) {
                val nodeError = errorConverter.convert(selected.node.id, e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }
}
