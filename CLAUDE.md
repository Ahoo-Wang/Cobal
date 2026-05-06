# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Cobal** is a reactive load balancing library for LLM/AI client SDKs, built on Kotlin coroutines and Resilience4j circuit breakers. The primary use case is distributing API requests across multiple LLM endpoints (each potentially with a different API key) to handle rate limiting transparently. Target scenario: SAAS platforms where tenants provide their own API keys and need per-tenant load balancer instances.

- **Group**: `me.ahoo.cobal` | **Version**: `0.2.0` | **JVM**: 17 | **Kotlin**: 2.3.20

## Build & Development Commands

```bash
# Full build (compile, test, check, package)
./gradlew build

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :core:test
./gradlew :langchain4j:test
./gradlew :spring-ai:test

# Run a single test class
./gradlew :core:test --tests "me.ahoo.cobal.RoundRobinLoadBalancerTest"

# Run a single test method
./gradlew :core:test --tests "me.ahoo.cobal.RoundRobinLoadBalancerTest.choose should return available node"

# Run code quality checks (Detekt)
./gradlew detekt

# Run detekt with auto-correct
./gradlew detekt --auto-correct

# Verify code coverage (Kover merged report)
./gradlew koverMergedVerify

# Generate documentation (Dokka)
./gradlew :core:dokka

# Clean build artifacts
./gradlew clean

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

## Module Architecture

```
core  (depends on: resilience4j-circuitbreaker, resilience4j-kotlin)
 |
 +-- langchain4j  (depends on: :core, langchain4j core + openai)
 |
 +-- spring-ai    (depends on: :core, spring-ai-model, reactor-core)

bom  (constraints on all library projects)
code-coverage-report  (aggregation only, not published)
```

- **`:core`** — Framework-agnostic abstractions: `Node`, `NodeState`, `LoadBalancer`, algorithms, `CobalError` hierarchy, `NodeErrorConverter`
- **`:langchain4j`** — LangChain4j integration; load-balanced decorators for all model types (chat, streaming chat, embedding, image, audio transcription)
- **`:spring-ai`** — Spring AI integration; symmetric to langchain4j module but for Spring AI model types
- **`:bom`** — Bill of Materials for centralized dependency management
- **`:code-coverage-report`** — Aggregated Kover coverage report

## Key Abstractions

**Node** (`core/.../Node.kt`) — Immutable endpoint with `id` and `weight`. `ModelNode<MODEL>` bridges to framework-specific model instances; `DefaultModelNode<MODEL>` is the concrete data class.

**NodeState** (`core/.../NodeState.kt`) — Bridges a `Node` with a Resilience4j `CircuitBreaker` via Kotlin delegation. A node is available when its weight is positive AND its circuit breaker is not in OPEN state. Delegates all `CircuitBreaker` methods (including `tryAcquirePermission`, `onResult`, `onError`) to the underlying Resilience4j instance.

**LoadBalancer** (`core/.../LoadBalancer.kt`) — Generic interface over `NODE : Node`. Holds `states: List<NodeState<NODE>>`; `choose()` returns a `NodeState<NODE>`. The `execute()` extension function provides the shared synchronous retry loop used by all integration models.

**CobalError** hierarchy (`core/.../error/`):
```
CobalError (open)
 +-- NodeError (holds nodeId)
 |    +-- RateLimitError    : RetriableError  (429)
 |    +-- ServerError       : RetriableError  (5xx)
 |    +-- TimeoutError      : RetriableError
 |    +-- NetworkError      : RetriableError
 |    +-- AuthenticationError                 (401/403, NOT retriable)
 |    +-- InvalidRequestError                 (400, NOT retriable)
 +-- AllNodesUnavailableError                 (whole LB failed)
```
`RetriableError` is a marker interface. `InvalidRequestError` short-circuits the retry loop via `throwIfInvalidRequest()` — bad requests won't succeed on another node.

**NodeErrorConverter** (`core/.../error/NodeErrorConverter.kt`) — `fun interface` that converts framework-specific exceptions to `NodeError`. Each integration module provides its own implementation.

**LoadBalancerRegistry** (`core/.../LoadBalancerRegistry.kt`) — Thread-safe `ConcurrentHashMap` registry. Used for tenant-scoped LB caching with `getOrCreate` (double-checked locking).

**Algorithms** (`core/.../algorithm/`): `AbstractLoadBalancer` (reactive state caching via `eventPublisher.onStateTransition`), `RandomLoadBalancer`, `RoundRobinLoadBalancer` (AtomicInteger-based), `WeightedRandomLoadBalancer` (Vose's Alias Method), `WeightedRoundRobinLoadBalancer` (Nginx smooth WRR). All throw `AllNodesUnavailableError` when no nodes available.

## Integration Module Pattern

Both `langchain4j` and `spring-ai` follow the same symmetric pattern:

1. **NodeErrorConverter** — Singleton object implementing `NodeErrorConverter`. LangChain4j maps typed exception classes; Spring AI matches HTTP status codes in exception messages.
2. **Model Node Type Aliases** — `typealias ChatModelNode = DefaultModelNode<ChatModel>` etc., defined in each model file.
3. **Load-Balanced Decorators** — Implement the framework's model interface via Kotlin `by delegate` delegation. Each override is a single call to `loadBalancer.execute(ErrorConverter) { it.method(args) }`.
4. **Streaming** — Both modules have their own async retry because the shared `execute()` is synchronous:
   - **LangChain4j**: `LoadBalancedStreamingChatModel` uses callback-based recursive retry with `RetryingHandler`
   - **Spring AI**: `LoadBalancedChatModel.stream()` uses Flux-based retry with `AtomicBoolean` emission tracking (does not retry after data emitted)

## Request Flow

```
LoadBalancedModel.method(request)
  → LoadBalancer.execute(converter) { model ->
      → LoadBalancer.choose()
        → Filter by available (weight > 0, circuit breaker not OPEN)
        → Select via algorithm
      → candidate.tryAcquirePermission()  // Resilience4j circuit breaker
      → model.method(request)
        → Success: candidate.onResult(duration, ...) → return response
        → Failure: converter.convert() → candidate.onError(duration, ...) → throwIfInvalidRequest()
        → Retriable: continue loop with next choose()
      → All attempts exhausted: throw AllNodesUnavailableError
  }
```

## Testing

- **Frameworks**: JUnit Jupiter (6.0.3) + Kotlin Test + MockK 1.14.9 + fluent-assert (`me.ahoo.test:fluent-assert-core` 0.2.6)
- **Reactive testing**: `reactor-test` StepVerifier for Spring AI streaming tests
- **Test naming**: Backtick descriptive names — `` `choose should skip unavailable node` ``
- **Assertions**: Mixed — `kotlin.test.assertEquals`/`assertTrue` and fluent `.assert()` extension; integration tests predominantly use `.assert()`
- **Mocking**: MockK for framework model interfaces in integration tests
- **Test retry**: In CI (`CI` env var), retries up to 2 times with 20 max failures
- **Test logging**: `-Dlogback.configurationFile=${rootDir}/config/logback.xml`
- **Compiler flags**: `-Xjsr305=strict` (strict null-safety), `-Xjvm-default=all-compatibility` (default interface methods)
