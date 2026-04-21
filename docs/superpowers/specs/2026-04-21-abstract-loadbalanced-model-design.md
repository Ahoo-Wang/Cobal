# AbstractLoadBalancedModel Design Spec

## Context

Nine synchronous `LoadBalanced*` classes across langchain4j (4) and spring-ai (4) — plus `LoadBalancedStreamingChatModel` — duplicate two pieces of logic verbatim:

1. **Retry loop**: `repeat(maxRetries) { choose() → try delegate → catch → toNodeError → onFailure }` — copied ~9 times
2. **`toNodeError()` companion object**: identical within each module, different between modules — copied 9 times

The only thing that varies per method is the single delegate call line: `selected.node.model.chat(request)` vs `selected.node.model.generate(prompt)` etc.

This spec introduces `AbstractLoadBalancedModel` in core to eliminate that duplication via the Template Method pattern.

## Scope

- **In scope**: Abstract base class with `executeWithRetry`, `ErrorConverter` strategy, refactor of 8 synchronous `LoadBalanced*` classes
- **Out of scope**: `LoadBalancedStreamingChatModel` (recursive callback pattern is fundamentally different), `LoadBalancer` interface, `NodeFailurePolicy`

## Architecture

### AbstractLoadBalancedModel

A generic abstract class in `me.ahoo.cobal` that holds the `LoadBalancer`, `maxRetries`, and an `ErrorConverter`. Subclasses call `executeWithRetry { it.specificMethod(args) }` to get the full retry-with-fallback behavior.

```kotlin
abstract class AbstractLoadBalancedModel<NODE : ModelNode<MODEL>, MODEL>(
    val loadBalancer: LoadBalancer<NODE>,
    val maxRetries: Int = 3,
    private val errorConverter: ErrorConverter
) {
    protected fun <T> executeWithRetry(block: (MODEL) -> T): T {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return block(selected.node.model)
            } catch (e: Throwable) {
                val nodeError = errorConverter.convert(selected.node.id, e)
                    ?: ServerError(selected.node.id, e as? Exception ?: RuntimeException(e.message, e))
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }
}
```

**Design decisions:**

- `executeWithRetry` is `protected` — only visible to subclasses
- Catches `Throwable` (not `Exception`) — matches existing langchain4j code that catches `Throwable` and casts to `Exception`
- When `errorConverter.convert()` returns `null`, falls back to `ServerError` — preserves current "else → ServerError" behavior in all `toNodeError` implementations
- `ErrorConverter` is a constructor parameter — modules inject their own conversion strategy

### ErrorConverter strategy

The `ErrorConverter` functional interface already exists in `CobalError.kt`:

```kotlin
fun interface ErrorConverter {
    fun convert(nodeId: NodeId, error: Throwable): CobalError?
    companion object {
        val Default = ErrorConverter { _, _ -> null }
    }
}
```

Each module provides its own instance:

**LangChain4j** (`LangChain4jErrorConverter.kt`):
```kotlin
val LangChain4jErrorConverter = ErrorConverter { nodeId, error ->
    when (error) {
        is dev.langchain4j.exception.RateLimitException -> RateLimitError(nodeId, error)
        is dev.langchain4j.exception.InvalidRequestException -> InvalidRequestError(nodeId, error)
        is dev.langchain4j.exception.AuthenticationException -> AuthenticationError(nodeId, error)
        is dev.langchain4j.exception.TimeoutException -> TimeoutError(nodeId, error)
        else -> null
    }
}
```

**Spring AI** (`SpringAiErrorConverter.kt`):
```kotlin
val SpringAiErrorConverter = ErrorConverter { nodeId, error ->
    val ex = error as? Exception ?: RuntimeException(error.message, error)
    when {
        error.message?.contains("429") == true -> RateLimitError(nodeId, ex)
        error.message?.contains("401") == true || error.message?.contains("403") == true -> AuthenticationError(nodeId, ex)
        error.message?.contains("400") == true -> InvalidRequestError(nodeId, ex)
        else -> null
    }
}
```

Returning `null` from the converter signals "unrecognized error" — `executeWithRetry` maps it to `ServerError`, preserving existing behavior.

### Subclass refactoring pattern

Each synchronous `LoadBalanced*` class is simplified to:

1. Extend `AbstractLoadBalancedModel<XModelNode, XModel>` instead of holding `loadBalancer`/`maxRetries` directly
2. Remove the `toNodeError` companion object
3. Each interface method becomes a one-liner: `override fun method(args) = executeWithRetry { it.method(args) }`

**Before** (langchain4j `LoadBalancedChatModel`, 49 lines):
```kotlin
class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ChatModelNode>,
    private val maxRetries: Int = 3
) : ChatModel {
    override fun chat(request: ChatRequest): ChatResponse {
        repeat(maxRetries) { attempt ->
            val selected = loadBalancer.choose()
            try { return selected.node.model.chat(request) }
            catch (e: Throwable) { /* toNodeError + onFailure + retry-or-throw */ }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }
    companion object { fun toNodeError(...): CobalError = ... }
}
```

**After** (~10 lines):
```kotlin
class LoadBalancedChatModel(
    loadBalancer: LoadBalancer<ChatModelNode>,
    maxRetries: Int = 3
) : AbstractLoadBalancedModel<ChatModelNode, ChatModel>(loadBalancer, maxRetries, LangChain4jErrorConverter),
    ChatModel {
    override fun chat(request: ChatRequest) = executeWithRetry { it.chat(request) }
}
```

### Not changed

- `LoadBalancedStreamingChatModel` — recursive callback pattern, out of scope
- `LoadBalancer<NODE>` interface — no changes
- `ErrorConverter` interface — already exists, no changes
- `NodeFailurePolicy` — orthogonal concern, no changes

## File map

| File | Action | Responsibility |
|------|--------|----------------|
| `core/src/main/kotlin/me/ahoo/cobal/AbstractLoadBalancedModel.kt` | Create | Abstract base with `executeWithRetry` template method |
| `langchain4j/src/main/kotlin/.../LangChain4jErrorConverter.kt` | Create | Module-level error converter (extracts `toNodeError` from companion objects) |
| `spring-ai/src/main/kotlin/.../SpringAiErrorConverter.kt` | Create | Module-level error converter (extracts `toNodeError` from companion objects) |
| `langchain4j/.../LoadBalancedChatModel.kt` | Modify | Extend AbstractLoadBalancedModel, remove companion object |
| `langchain4j/.../LoadBalancedEmbeddingModel.kt` | Modify | Extend AbstractLoadBalancedModel, remove companion object |
| `langchain4j/.../LoadBalancedImageModel.kt` | Modify | Extend AbstractLoadBalancedModel, remove companion object |
| `langchain4j/.../LoadBalancedAudioTranscriptionModel.kt` | Modify | Extend AbstractLoadBalancedModel, remove companion object |
| `spring-ai/.../LoadBalancedChatModel.kt` | Modify | Extend AbstractLoadBalancedModel, remove companion object |
| `spring-ai/.../LoadBalancedEmbeddingModel.kt` | Modify | Extend AbstractLoadBalancedModel, remove companion object |
| `spring-ai/.../LoadBalancedImageModel.kt` | Modify | Extend AbstractLoadBalancedModel, remove companion object |
| `spring-ai/.../LoadBalancedAudioTranscriptionModel.kt` | Modify | Extend AbstractLoadBalancedModel, remove companion object |
| `core/src/test/kotlin/.../AbstractLoadBalancedModelTest.kt` | Create | Unit tests for executeWithRetry: success, retry on failure, all-nodes-unavailable |

## Testing

- **Unit test** (`AbstractLoadBalancedModelTest`): test `executeWithRetry` directly — success path, failure+retry path, exhausted retries throws `AllNodesUnavailableError`
- **Regression**: all existing `LoadBalanced*Test` classes in both modules must continue to pass
- **Detekt**: no new violations
