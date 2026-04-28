package me.ahoo.cobal.dsl

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.kotlin.circuitbreaker.CircuitBreakerConfig
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.state.DEFAULT_CIRCUIT_BREAKER_CONFIG

@CobalDsl
class NodeBuilder<MODEL : Any>(
    private val id: String,
    private val weight: Int,
) {
    private var model: MODEL? = null
    private var circuitBreakerConfig: CircuitBreakerConfig = DEFAULT_CIRCUIT_BREAKER_CONFIG

    fun model(model: MODEL) {
        this.model = model
    }

    fun circuitBreaker(block: CircuitBreakerConfig.Builder.() -> Unit) {
        this.circuitBreakerConfig = CircuitBreakerConfig(block)
    }

    fun build(): Pair<DefaultModelNode<MODEL>, CircuitBreakerConfig> {
        val model = checkNotNull(model) {
            "model() must be called exactly once for node '$id'."
        }
        return DefaultModelNode(id, weight, model) to circuitBreakerConfig
    }
}
