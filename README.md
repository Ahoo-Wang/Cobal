# Cobal

Reactive load balancing for LLM/AI client SDKs, built natively on Kotlin coroutines and Resilience4j circuit breakers.

## Why Cobal?

Distribute API requests across multiple LLM endpoints — each with its own API key — to handle rate limiting transparently. Designed for SaaS platforms where tenants provide their own keys and need per-tenant load balancer instances.

## Features

- **Load Balancing Algorithms** — Random, RoundRobin, WeightedRandom (Alias Method), WeightedRoundRobin (Nginx smooth WRR)
- **Circuit Breaker** — Built on Resilience4j; auto-opens on consecutive failures, skips unhealthy endpoints
- **Automatic Retry** — Retriable errors (429, 5xx, timeout, network) trigger failover to the next node
- **Smart Short-Circuit** — Invalid requests (400) and auth errors (401/403) fail immediately without wasting retries
- **Streaming Support** — Callback-based retry for LangChain4j, Flux-based retry for Spring AI
- **Tenant-Scoped Registry** — Thread-safe `LoadBalancerRegistry` for per-tenant LB caching

## Installation

### Gradle (Kotlin DSL)

```kotlin
// Use the BOM for version alignment
implementation(platform("me.ahoo.cobal:cobal-bom:0.0.1"))

// Core only (framework-agnostic)
implementation("me.ahoo.cobal:cobal-core")

// LangChain4j integration
implementation("me.ahoo.cobal:cobal-langchain4j")

// Spring AI integration
implementation("me.ahoo.cobal:cobal-spring-ai")
```

### Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cobal</groupId>
            <artifactId>cobal-bom</artifactId>
            <version>0.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>me.ahoo.cobal</groupId>
    <artifactId>cobal-langchain4j</artifactId>
</dependency>
```

## Quick Start

### LangChain4j

```kotlin
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.algorithm.RoundRobinLoadBalancer
import me.ahoo.cobal.langchain4j.LoadBalancedChatModel
import me.ahoo.cobal.state.DefaultNodeState
import dev.langchain4j.model.openai.OpenAiChatModel

// Create model instances (each with a different API key)
val models = listOf(
    OpenAiChatModel.builder().apiKey("key-1").build(),
    OpenAiChatModel.builder().apiKey("key-2").build(),
    OpenAiChatModel.builder().apiKey("key-3").build(),
)

// Wrap each as a node with circuit breaker
val nodes = models.mapIndexed { i, model ->
    DefaultModelNode("endpoint-$i", model = model)
}
val states = nodes.map { DefaultNodeState(it) }

// Create load balancer
val loadBalancer = RoundRobinLoadBalancer("my-tenant-lb", states)

// Use the load-balanced model
val balancedModel = LoadBalancedChatModel(loadBalancer)
val response = balancedModel.chat("Tell me a joke")
```

### Spring AI

```kotlin
import me.ahoo.cobal.DefaultModelNode
import me.ahoo.cobal.algorithm.WeightedRoundRobinLoadBalancer
import me.ahoo.cobal.springai.LoadBalancedChatModel
import me.ahoo.cobal.state.DefaultNodeState

// Create nodes with different weights
val nodes = listOf(
    DefaultModelNode("primary", weight = 5, model = primaryChatModel),
    DefaultModelNode("secondary", weight = 3, model = secondaryChatModel),
    DefaultModelNode("fallback", weight = 1, model = fallbackChatModel),
)
val states = nodes.map { DefaultNodeState(it) }

val loadBalancer = WeightedRoundRobinLoadBalancer("tenant-lb", states)
val balancedModel = LoadBalancedChatModel(loadBalancer)

// Sync call
val response = balancedModel.call(prompt)

// Reactive streaming (with automatic retry before first emission)
val stream = balancedModel.stream(prompt)
```

## Load Balancing Algorithms

| Algorithm | Selection | Use Case |
|-----------|-----------|----------|
| `RandomLoadBalancer` | Uniform random | Simple distribution |
| `RoundRobinLoadBalancer` | Sequential round-robin | Equal-weight endpoints |
| `WeightedRandomLoadBalancer` | Alias Method (O(1)) | Unequal capacity, probabilistic |
| `WeightedRoundRobinLoadBalancer` | Nginx smooth WRR | Deterministic weighted distribution |

## Error Handling

Cobal classifies errors to decide whether to retry:

| Error | HTTP | Retriable | Behavior |
|-------|------|-----------|----------|
| `RateLimitError` | 429 | Yes | Retry on next node |
| `ServerError` | 5xx | Yes | Retry on next node |
| `TimeoutError` | — | Yes | Retry on next node |
| `NetworkError` | — | Yes | Retry on next node |
| `AuthenticationError` | 401/403 | No | Fail immediately |
| `InvalidRequestError` | 400 | No | Fail immediately, ignored by circuit breaker |
| `AllNodesUnavailableError` | — | — | All retry attempts exhausted |

## Tenant-Scoped Load Balancers

```kotlin
val registry = DefaultLoadBalancerRegistry()

// Get or create a per-tenant load balancer
val lb = registry.getOrCreate("tenant-123") {
    RoundRobinLoadBalancer("tenant-123", tenantNodes.map { DefaultNodeState(it) })
}
```

## Module Architecture

```
core  (kotlinx-coroutines, resilience4j-circuitbreaker)
 ├── langchain4j  (langchain4j core + openai)
 └── spring-ai    (spring-ai-model, reactor-core)

bom  (version alignment)
```

## Requirements

- **JVM**: 17+
- **Kotlin**: 2.3.20

## Building

```bash
./gradlew build          # Full build
./gradlew test           # Run all tests
./gradlew :core:test     # Run tests for a specific module
./gradlew detekt         # Code quality checks
```

## License

[Apache License 2.0](LICENSE)
