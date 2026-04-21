# Cobal Load Balancer SDK Design

## Overview

Cobal is a reactive load balancing library for LLM client SDKs, built natively on Kotlin coroutines. It solves LLM rate limiting by distributing requests across multiple API keys/endpoints with transparent load balancing.

**Primary use case**: SAAS platforms where tenants provide their own API keys, requiring per-tenant LoadBalancer instances with application-level in-memory caching.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Instance lifecycle | Hybrid: core provides stateful + stateless algorithms, caching decided by integration modules | Maximum flexibility |
| Built-in algorithms | Random + RoundRobin + WeightedRoundRobin | Sufficient for LLM scenarios |
| Caching scope | Application-level in-memory only | SAAS per-tenant, no distributed state |
| Node identity | NodeId = ModelId (one Model instance = one Node) | Clean mental model |
| ModelNode bridge | `ModelNode<MODEL>` in core, MODEL typed by integration module | Core stays framework-agnostic |
| Rate limit handling | NodeState.onFailure(NodeError) with pluggable NodeFailurePolicy | Core provides mechanism + ErrorCategory taxonomy, strategy by integration module |
| Node availability | NodeStatus enum on NodeState, LoadBalancer.choose() skips unavailable nodes | Transparent to caller |
| Integration API | Decorator pattern (LoadBalancedChatModel : ChatLanguageModel) | Zero code change for users |
| WatchableNode | Not needed initially, events on NodeState instead | State changes belong to NodeState, not immutable Node |

## Module Architecture

```
cobal (root)
├── core          — Node, ModelNode, NodeState, NodeStatus, NodeEvent, LoadBalancer, algorithms, LoadBalancerRegistry
├── langchain4j   — ModelNode implementations, decorators, FailurePolicy
├── spring-ai     — ModelNode implementations, decorators, FailurePolicy
├── bom           — Bill of Materials
└── code-coverage-report
```

## Core Module

### Node Abstractions

```kotlin
typealias NodeId = String

interface Node {
    val id: NodeId
    val weight: Int
        get() = 1
}

interface ModelNode<MODEL> : Node {
    val model: MODEL
}
```

- `Node` is immutable, represents a load-balanced endpoint
- `ModelNode<MODEL>` bridges load balancing and model client; `MODEL` is framework-specific (e.g., `ChatLanguageModel` in LangChain4j, `ChatModel` in Spring AI)
- NodeId = ModelId, uniquely identifying a model instance (specific API key + endpoint combination)

### NodeStatus

```kotlin
enum class NodeStatus {
    AVAILABLE,       // Healthy, can be chosen by LoadBalancer
    UNAVAILABLE,     // Rate-limited or temporary failure, auto-recovers at recoverAt
    CIRCUIT_OPEN     // Consecutive failures exceeded threshold, longer cooldown required
}
```

- `AVAILABLE`: Normal state, eligible for `choose()`
- `UNAVAILABLE`: Triggered by rate limit (429), recovers automatically when `recoverAt` is reached
- `CIRCUIT_OPEN`: Triggered by consecutive failures exceeding threshold, prevents repeated retries on dead nodes

### NodeEvent

```kotlin
sealed interface NodeEvent {
    val nodeId: NodeId
    data class MarkedUnavailable(override val nodeId: NodeId, val recoverAt: Instant) : NodeEvent
    data class Recovered(override val nodeId: NodeId) : NodeEvent
}
```

- Events are emitted by `NodeState` on state transitions
- External systems can subscribe via `NodeState.watch: Flow<NodeEvent>` for logging, metrics, alerting

### NodeError

```kotlin
enum class ErrorCategory {
    RATE_LIMITED,        // 429 — rate limited, should mark node unavailable
    SERVER_ERROR,        // 5xx — server-side error, may retry on another node
    AUTHENTICATION,      // 401/403 — invalid/expired API key, node permanently unusable
    INVALID_REQUEST,     // 400 — bad parameters, caller error, no node state change
    TIMEOUT,             // Request timeout, may indicate node degradation
    NETWORK              // Connection failure, network-level issue
}

class NodeError(
    val category: ErrorCategory,
    override val cause: Throwable
) : Exception(cause?.message, cause)
```

- `ErrorCategory` provides a unified error taxonomy across LLM providers
- Integration modules translate framework-specific exceptions into `NodeError` with appropriate `ErrorCategory`
- `NodeFailurePolicy` makes decisions based on `ErrorCategory` instead of raw exception types

### NodeFailurePolicy

```kotlin
data class NodeFailureDecision(val recoverAt: Instant)

fun interface NodeFailurePolicy {
    fun evaluate(error: NodeError): NodeFailureDecision?

    companion object {
        val Default = NodeFailurePolicy { null }
    }
}
```

- Core defines the interface and a no-op default
- Integration modules provide `ErrorCategory`-aware implementations
- `NodeState.onFailure(error)` delegates to `NodeFailurePolicy` to decide whether to mark the node unavailable

### NodeState

```kotlin
interface NodeState<NODE : Node> {
    val node: NODE
    val watch: Flow<NodeEvent>
    val status: NodeStatus
    val available: Boolean
        get() = status == NodeStatus.AVAILABLE

    fun onFailure(error: NodeError)
}

class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    private val failurePolicy: NodeFailurePolicy = NodeFailurePolicy.Default,
    private val circuitOpenThreshold: Int = 5
) : NodeState<NODE> {
    private var recoverAt: Instant? = null
    private var failureCount: Int = 0
    private val events = MutableSharedFlow<NodeEvent>()

    override val watch: Flow<NodeEvent> = events.asSharedFlow()

    override val status: NodeStatus
        get() {
            val currentRecoverAt = recoverAt
            return when {
                failureCount >= circuitOpenThreshold -> NodeStatus.CIRCUIT_OPEN
                currentRecoverAt != null && currentRecoverAt.isAfter(Clock.systemUTC().instant()) -> NodeStatus.UNAVAILABLE
                else -> {
                    if (failureCount > 0 || currentRecoverAt != null) {
                        failureCount = 0
                        recoverAt = null
                        events.tryEmit(NodeEvent.Recovered(node.id))
                    }
                    NodeStatus.AVAILABLE
                }
            }
        }

    override fun onFailure(error: NodeError) {
        failurePolicy.evaluate(error)?.let { decision ->
            this.recoverAt = decision.recoverAt
            failureCount++
            events.tryEmit(NodeEvent.MarkedUnavailable(node.id, decision.recoverAt))
        }
    }
}
```

- `NodeState` is an interface for extensibility and testing
- `DefaultNodeState` provides the standard implementation with failure counting and auto-recovery
- `status` is derived from `recoverAt` and `failureCount`, always consistent
- `onFailure(exception)` is the single entry point for error handling; `NodeFailurePolicy` decides the action
- `choose()` only selects nodes where `status == AVAILABLE`

### LoadBalancer

```kotlin
typealias LoadBalancerId = String

interface LoadBalancer<NODE : Node> {
    val id: LoadBalancerId
    val nodes: List<NODE>
    fun choose(): NodeState<NODE>
}
```

- `choose()` returns `NodeState<NODE>` instead of `NODE`, giving the caller direct access to state management
- `choose()` only selects from nodes with `status == AVAILABLE`
- `LoadBalancerId` serves as the cache key in `LoadBalancerRegistry`

### Algorithms

Three built-in implementations:

| Algorithm | Stateful | Description |
|-----------|----------|-------------|
| `RandomLoadBalancer` | No | Random selection from available nodes |
| `RoundRobinLoadBalancer` | Yes | Sequential rotation, supports position memory |
| `WeightedRoundRobinLoadBalancer` | Yes | Weight-based distribution, respects `Node.weight` |

All algorithms filter by `NodeStatus.AVAILABLE` before selection.

### LoadBalancerRegistry

```kotlin
class LoadBalancerRegistry {
    private val registry = ConcurrentHashMap<LoadBalancerId, LoadBalancer<*>>()

    fun <NODE : Node> getOrCreate(id: LoadBalancerId, factory: () -> LoadBalancer<NODE>): LoadBalancer<NODE>
    fun <NODE : Node> get(id: LoadBalancerId): LoadBalancer<NODE>?
    fun remove(id: LoadBalancerId): LoadBalancer<*>?
    fun contains(id: LoadBalancerId): Boolean
}
```

- Application-level in-memory registry, no distributed state
- `getOrCreate()` for SAAS tenant-scoped LoadBalancer instances
- `remove()` for tenant offboarding / cleanup
- Thread-safe via `ConcurrentHashMap`

## Integration Modules

### LangChain4j Module

**ModelNode implementations:**

```kotlin
class ChatModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: ChatLanguageModel
) : ModelNode<ChatLanguageModel>
```

Same pattern for `EmbeddingModelNode`, `ImageModelNode`, `AudioTranscriptionModelNode`.

**Decorator pattern — LoadBalancedChatModel:**

```kotlin
class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ModelNode<ChatLanguageModel>>,
    private val maxRetries: Int = 3
) : ChatLanguageModel {

    override fun chat(request: ChatRequest): ChatResponse {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.chat(request)
            } catch (e: Exception) {
                val nodeError = toNodeError(e)  // Translate framework exception to NodeError
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }
}
```

- Transparent proxy: users use it as a regular `ChatLanguageModel`
- On failure: `NodeState.onFailure(e)` delegates to `NodeFailurePolicy`, then retries with `choose()`
- Same pattern for all model types (StreamingChat, Embedding, Image, Audio)

**LangChain4jFailurePolicy:**

```kotlin
val LangChain4jFailurePolicy = NodeFailurePolicy { error ->
    when (error.category) {
        ErrorCategory.RATE_LIMITED -> NodeFailureDecision(
            recoverAt = Instant.now() + (error.cause.retryAfter ?: Duration.ofSeconds(30))
        )
        ErrorCategory.AUTHENTICATION -> NodeFailureDecision(
            recoverAt = Instant.now() + Duration.ofHours(1) // Key invalid, long cooldown
        )
        else -> null
    }
}
```

**Full model type coverage:**

| Model Type | Decorator | Proxy Interface |
|-----------|-----------|----------------|
| Chat | `LoadBalancedChatModel` | `ChatLanguageModel` |
| Streaming Chat | `LoadBalancedStreamingChatModel` | `StreamingChatLanguageModel` |
| Embedding | `LoadBalancedEmbeddingModel` | `EmbeddingModel` |
| Image | `LoadBalancedImageModel` | `ImageModel` |
| Audio | `LoadBalancedAudioTranscriptionModel` | `AudioTranscriptionModel` |

### Spring AI Module

Symmetric design with LangChain4j module, adapting Spring AI model interfaces:

| Model Type | Decorator | Proxy Interface |
|-----------|-----------|----------------|
| Chat | `LoadBalancedChatModel` | `ChatModel` |
| Embedding | `LoadBalancedEmbeddingModel` | `EmbeddingModel` |
| Image | `LoadBalancedImageModel` | `ImageModel` |
| Audio | `LoadBalancedAudioTranscriptionModel` | `AudioTranscriptionModel` |

With a `SpringAiFailurePolicy` that detects Spring AI specific exception types.

## User API

### Builder API

```kotlin
val loadBalancedChatModel = LoadBalancedChatModel.builder()
    .addModel("openai-key-1", ChatLanguageModel.builder()
        .apiKey("sk-xxx1")
        .modelName("gpt-4o")
        .build())
    .addModel("openai-key-2", ChatLanguageModel.builder()
        .apiKey("sk-xxx2")
        .modelName("gpt-4o")
        .build())
    .algorithm(LoadBalancerAlgorithm.WEIGHTED_ROUND_ROBIN)
    .failurePolicy(LangChain4jFailurePolicy)
    .maxRetries(3)
    .build()

// Use as regular ChatLanguageModel — zero code change
val response = loadBalancedChatModel.chat("Hello")
```

### SAAS Tenant-Scoped Usage

```kotlin
val registry = LoadBalancerRegistry()

fun handleRequest(tenantId: String, apiKeys: List<String>): ChatResponse {
    val lb = registry.getOrCreate("chat:gpt-4o:$tenantId") {
        LoadBalancedChatModel.builder()
            .addModels(apiKeys.map { key ->
                ChatModelNode(id = key, model = createChatModel(key))
            })
            .build()
            .loadBalancer
    }
    return LoadBalancedChatModel(lb).chat("Hello")
}

// Tenant offboarding
registry.remove("chat:gpt-4o:tenant-123")
```

## Request Flow

```
User calls LoadBalancedChatModel.chat(request)
  → LoadBalancer.choose()
    → Filter nodes by NodeStatus.AVAILABLE
    → Select node via algorithm (Random / RoundRobin / WeightedRoundRobin)
    → Return NodeState
  → NodeState.node.model.chat(request)
    → Success: return ChatResponse
    → Failure: translate exception to NodeError(ErrorCategory)
      → NodeState.onFailure(nodeError)
      → NodeFailurePolicy.evaluate(nodeError)
        → Returns NodeFailureDecision → mark UNAVAILABLE/CIRCUIT_OPEN
        → Returns null → no state change
      → Re-enter choose() for retry (up to maxRetries)
    → All nodes unavailable after retries: throw AllNodesUnavailableError
```

## Error Handling

| Scenario | ErrorCategory | Handling |
|----------|--------------|----------|
| 429 rate limit | `RATE_LIMITED` | Mark node UNAVAILABLE with recoverAt from Retry-After header, retry on another node |
| 401/403 auth failure | `AUTHENTICATION` | Mark node with long cooldown, API key is invalid |
| 5xx server error | `SERVER_ERROR` | No node state change by default, exception propagates to caller |
| 400 bad request | `INVALID_REQUEST` | No node state change, caller error |
| Consecutive failures | (any category) | Mark node CIRCUIT_OPEN after threshold exceeded |
| All nodes unavailable | — | Throw `AllNodesUnavailableError` |

## Package Structure

### Core

```
me.ahoo.cobal
├── Node.kt                         — Node, ModelNode<MODEL>
├── NodeState.kt                    — NodeState<NODE>, DefaultNodeState, NodeStatus, NodeEvent, NodeError, ErrorCategory, NodeFailureDecision, NodeFailurePolicy
├── LoadBalancer.kt                 — LoadBalancer<NODE>
├── LoadBalancerRegistry.kt         — LoadBalancerRegistry
└── algorithm/
    ├── RandomLoadBalancer.kt
    ├── RoundRobinLoadBalancer.kt
    └── WeightedRoundRobinLoadBalancer.kt
```

### LangChain4j

```
me.ahoo.cobal.langchain4j
├── model/
│   ├── ChatModelNode.kt
│   ├── EmbeddingModelNode.kt
│   ├── ImageModelNode.kt
│   └── AudioTranscriptionModelNode.kt
├── LoadBalancedChatModel.kt
├── LoadBalancedStreamingChatModel.kt
├── LoadBalancedEmbeddingModel.kt
├── LoadBalancedImageModel.kt
├── LoadBalancedAudioTranscriptionModel.kt
└── LangChain4jFailurePolicy.kt
```

### Spring AI

```
me.ahoo.cobal.springai
├── model/
│   ├── ChatModelNode.kt
│   ├── EmbeddingModelNode.kt
│   ├── ImageModelNode.kt
│   └── AudioTranscriptionModelNode.kt
├── LoadBalancedChatModel.kt
├── LoadBalancedEmbeddingModel.kt
├── LoadBalancedImageModel.kt
├── LoadBalancedAudioTranscriptionModel.kt
└── SpringAiFailurePolicy.kt
```
