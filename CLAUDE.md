# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Cobal** is a reactive load balancing library for LLM/AI client SDKs, built natively on Kotlin coroutines. The primary use case is distributing API requests across multiple LLM endpoints (each potentially with a different API key) to handle rate limiting transparently. Target scenario: SAAS platforms where tenants provide their own API keys and need per-tenant load balancer instances.

- **Group**: `me.ahoo.cobal` | **Version**: `0.0.1` | **JVM**: 17 | **Kotlin**: 2.3.20

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
core  (depends on: kotlinx-coroutines-core only)
 |
 +-- langchain4j  (depends on: :core, langchain4j core + openai)
 |
 +-- spring-ai    (depends on: :core, spring-ai-model)

bom  (constraints on all library projects)
code-coverage-report  (aggregation only, not published)
```

- **`:core`** — Framework-agnostic abstractions: `Node`, `NodeState`, `LoadBalancer`, algorithms, `CobalError` hierarchy, `NodeFailurePolicy`
- **`:langchain4j`** — LangChain4j integration; load-balanced decorators for all model types (chat, streaming chat, embedding, etc.)
- **`:spring-ai`** — Spring AI integration; symmetric to langchain4j module but for Spring AI model types
- **`:bom`** — Bill of Materials for centralized dependency management
- **`:code-coverage-report`** — Aggregated Kover coverage report

## Key Abstractions

**Node** (`core/.../Node.kt`) — Immutable endpoint with `id` and `weight`. `ModelNode<MODEL>` bridges to framework-specific model instances; `DefaultModelNode<MODEL>` is the concrete data class.

**NodeState** (`core/.../NodeState.kt`) — Stateful counterpart to `Node`. Implements circuit-breaker pattern with three statuses: `AVAILABLE`, `UNAVAILABLE` (temporary, auto-recovers after `recoverAt`), `CIRCUIT_OPEN` (consecutive failures ≥ threshold, default 5). Exposes `watch: Flow<NodeEvent>` for external observation. `onFailure()` delegates to `NodeFailurePolicy` to decide state transitions.

**LoadBalancer** (`core/.../LoadBalancer.kt`) — Generic interface over `NODE : Node`. Holds `states: List<NodeState<NODE>>`; `choose()` returns a `NodeState<NODE>` giving callers direct access to state management. `availableStates` filters by availability.

**CobalError** hierarchy (`core/.../CobalError.kt`):
```
CobalError (abstract)
 +-- NodeError (holds nodeId)
 |    +-- RateLimitError    : RetriableError  (429)
 |    +-- ServerError       : RetriableError  (5xx)
 |    +-- TimeoutError      : RetriableError
 |    +-- NetworkError      : RetriableError
 |    +-- AuthenticationError                 (401/403, NOT retriable)
 |    +-- InvalidRequestError                 (400, NOT retriable)
 +-- AllNodesUnavailableError                 (whole LB failed)
```
`RetriableError` is a marker interface. `NodeFailurePolicy.Default` marks retriable-error nodes unavailable for 30s; non-retriable errors produce no state change.

**LoadBalancerRegistry** (`core/.../LoadBalancerRegistry.kt`) — Thread-safe `ConcurrentHashMap` registry. Used for tenant-scoped LB caching with `getOrCreate` (double-checked locking).

**Algorithms** (`core/.../algorithm/`): `RandomLoadBalancer`, `RoundRobinLoadBalancer` (AtomicInteger-based), `WeightedRoundRobinLoadBalancer`. All throw `AllNodesUnavailableError` when no nodes available.

## Integration Module Pattern

Both `langchain4j` and `spring-ai` follow the same symmetric pattern:

1. **Model Node Type Aliases** — `typealias ChatModelNode = DefaultModelNode<ChatModel>` etc.
2. **Load-Balanced Decorators** — Implement the framework's model interface, wrapping `LoadBalancer<ModelNode>` with retry loop: `choose()` → delegate to `node.model.<method>()` → on failure: translate to `CobalError`, call `onFailure()`, retry → all retries exhausted: throw `AllNodesUnavailableError`
3. **Error Translation** — `companion object.toNodeError()` maps framework exceptions to `CobalError`. LangChain4j uses typed exception classes; Spring AI uses HTTP status code matching on exception messages.
4. **Failure Policy** — Framework-specific `NodeFailurePolicy` with different recovery durations (e.g., RateLimitError → 30s, AuthenticationError → 1h)

**Streaming exception**: `LoadBalancedStreamingChatModel` uses recursive callback-based retry instead of the synchronous `repeat` loop.

## Request Flow

```
LoadBalancedModel.method(request)
  → LoadBalancer.choose()
    → Filter by NodeStatus.AVAILABLE
    → Select via algorithm (Random / RoundRobin / WeightedRoundRobin)
  → node.model.method(request)
    → Success: return response
    → Failure: translate to CobalError → NodeState.onFailure() → retry with choose()
    → All retries exhausted: throw AllNodesUnavailableError
```

## Testing

- **Frameworks**: JUnit Jupiter (6.0.3) + Kotlin Test + MockK 1.14.9 + fluent-assert (`me.ahoo.test:fluent-assert-core` 0.2.6)
- **Test naming**: Backtick descriptive names — `` `choose should skip unavailable node` ``
- **Assertions**: Mixed — `kotlin.test.assertEquals`/`assertTrue` and fluent `.assert()` extension; integration tests predominantly use `.assert()`
- **Mocking**: MockK for framework model interfaces in integration tests
- **Test retry**: In CI (`CI` env var), retries up to 2 times with 20 max failures
- **Test logging**: `-Dlogback.configurationFile=${rootDir}/config/logback.xml`
