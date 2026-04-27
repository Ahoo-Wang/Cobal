# Spring AI Module Design

## Context

The `spring-ai` module was removed during the Resilience4j circuit breaker migration. It needs to be reimplemented following the same symmetric pattern as the `langchain4j` module, adapted for Spring AI's API (Reactor-based streaming, message-based error classification).

## Scope

Load-balanced decorators for Spring AI model interfaces: `ChatModel`, `EmbeddingModel`, `ImageModel`, `TranscriptionModel`.

## Architecture

### File Structure

```
spring-ai/src/main/kotlin/me/ahoo/cobal/springai/
  SpringAiNodeErrorConverter.kt
  LoadBalancedChatModel.kt
  LoadBalancedEmbeddingModel.kt
  LoadBalancedImageModel.kt
  LoadBalancedAudioTranscriptionModel.kt

spring-ai/src/test/kotlin/me/ahoo/cobal/springai/
  SpringAiNodeErrorConverterTest.kt
  LoadBalancedChatModelTest.kt
  LoadBalancedEmbeddingModelTest.kt
  LoadBalancedImageModelTest.kt
  LoadBalancedAudioTranscriptionModelTest.kt
```

### 1. SpringAiNodeErrorConverter

Singleton `NodeErrorConverter` that maps Spring AI exceptions to `NodeError` hierarchy.

Spring AI does not have typed exception classes like LangChain4j — errors are generic `RuntimeException` with HTTP status codes embedded in messages. The converter uses message pattern matching:

| Pattern | Mapped Error | Retriable |
|---------|-------------|-----------|
| `"429"` in message | `RateLimitError` | Yes |
| `"401"` or `"403"` in message | `AuthenticationError` | No |
| `"400"` in message | `InvalidRequestError` | No |
| `"5xx"` in message | `ServerError` | Yes |
| `"timeout"` in message | `TimeoutError` | Yes |
| `ConnectException`, `SocketTimeoutException`, `IOException` | `NetworkError` | Yes |
| Cause chain unwrapping (max depth 5) | Recursive match | — |
| Fallback | Generic `NodeError` | No |

### 2. LoadBalancedChatModel

Implements `ChatModel` (which extends `StreamingChatModel`). Combined sync + streaming.

- `call(Prompt)` → `loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(prompt) }`
- `stream(Prompt)` → Custom `Flux<ChatResponse>` retry with `AtomicBoolean` emission tracking. Does not retry after data has been emitted to the subscriber.

Streaming retry logic:
```
stream():
  if remainingRetries <= 0 → Flux.error(AllNodesUnavailableError)
  choose() → candidate
  if !tryAcquirePermission() → recurse with remainingRetries - 1
  candidate.node.model.stream(prompt)
    .doOnNext { emitted = true }
    .doOnComplete { candidate.onResult(...) }
    .onErrorResume { error ->
      if emitted → Flux.error(error)  // mid-stream failure, don't retry
      else → convert error, candidate.onError(...), recurse
    }
```

### 3. Sync Models (Embedding, Image, AudioTranscription)

Each follows the langchain4j pattern:
- Inline `typealias XxxModelNode = DefaultModelNode<FrameworkModel>`
- Kotlin `by delegate` delegation to first node's model
- Override primary method with single-line `loadBalancer.execute(SpringAiNodeErrorConverter) { it.call(request) }`

| Class | Implements | Overrides |
|-------|-----------|-----------|
| `LoadBalancedEmbeddingModel` | `EmbeddingModel` | `call(EmbeddingRequest)`, `embed(Document)` |
| `LoadBalancedImageModel` | `ImageModel` | `call(ImagePrompt)` |
| `LoadBalancedAudioTranscriptionModel` | `TranscriptionModel` | `call(AudioTranscriptionPrompt)` |

### 4. Build Configuration

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone/") }
}

dependencies {
    api(project(":core"))
    api(libs.spring.ai.model)
    implementation(libs.reactor.core)
    testImplementation(libs.reactor.test)
}
```

### Removed from Old Module

- `SpringAiFailurePolicy` — Resilience4j circuit breakers handle failure policies
- `NodeState.kt` extension functions — Core module handles node state directly
- `model/` subpackage — Type aliases inlined into each model file
- `AbstractLoadBalancedModel` base class — Replaced by `LoadBalancer.execute()` extension
- Separate `StreamingChatModel` decorator — `ChatModel` already extends `StreamingChatModel`

## Testing

- `SpringAiNodeErrorConverterTest` — Cover each status code mapping, cause chain unwrapping, fallback
- `LoadBalancedChatModelTest` — Sync delegation, sync AllNodesUnavailableError, streaming success, streaming retry before emission, no-retry after emission, streaming AllNodesUnavailableError, circuit breaker skip
- `LoadBalancedEmbeddingModelTest`, `LoadBalancedImageModelTest`, `LoadBalancedAudioTranscriptionModelTest` — Basic delegation + AllNodesUnavailableError each
