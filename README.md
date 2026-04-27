# Cobal

Load balancing library for LLM/AI client SDKs with Resilience4j circuit breakers.

## Overview

Cobal distributes API requests across multiple LLM endpoints to handle rate limiting transparently. Each endpoint can have its own API key, making Cobal ideal for **SAAS platforms where tenants provide their own API keys** and need per-tenant load balancer instances.

## Features

- **Multiple Load Balancing Algorithms**: Random, Round-Robin, Weighted Random (O(1) via Vose's Alias Method), and Smooth Weighted Round-Robin (Nginx-style)
- **Circuit Breaker Integration**: Built on Resilience4j for fault tolerance with per-endpoint health tracking
- **Transparent Retries**: Automatic retry with configurable error handling
- **Framework Integrations**:
  - LangChain4j (`me.ahoo.cobal:langchain4j`)
  - Spring AI (`me.ahoo.cobal:spring-ai`)
- **Type-safe Error Handling**: Hierarchical error model with retriable/non-retriable classification
- **Tenant-scoped Instances**: Thread-safe registry for per-tenant load balancer caching

## Modules

```
core  ── Framework-agnostic abstractions (Node, LoadBalancer, algorithms)
  │
  ├── langchain4j  ── LangChain4j integration (Chat, Streaming, Embedding, Image, Audio)
  └── spring-ai    ── Spring AI integration (Chat, Embedding, Image, Audio)

bom  ── Bill of Materials for centralized dependency management
```

## Quick Start

### Gradle Setup

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// core module
implementation("me.ahoo.cobal:core:0.0.2")

// LangChain4j integration
implementation("me.ahoo.cobal:langchain4j:0.0.2")

// Spring AI integration
implementation("me.ahoo.cobal:spring-ai:0.0.2")

// Or use BOM
implementation(platform("me.ahoo.cobal:bom:0.0.2"))
implementation("me.ahoo.cobal:core")
implementation("me.ahoo.cobal:langchain4j")
```

### Basic Usage

```kotlin
import me.ahoo.cobal.*
import me.ahoo.cobal.langchain4j.*

// Create nodes with models
val model1 = OpenAiChatModel.builder().apiKey("key1").build()
val model2 = OpenAiChatModel.builder().apiKey("key2").build()

val node1 = DefaultModelNode("node-1", weight = 5, model = model1)
val node2 = DefaultModelNode("node-2", weight = 1, model = model2)

// Create load balancer
val lb = RoundRobinLoadBalancer("my-lb", listOf(
    NodeState(node1, CircuitBreakers.of("node-1")),
    NodeState(node2, CircuitBreakers.of("node-2"))
))

// Use as drop-in replacement
val balancedModel = LoadBalancedChatModel(lb)
val response = balancedModel.chat(ChatRequest("Hello!"))
```

### Supported Algorithms

| Algorithm | Description |
|-----------|-------------|
| `RandomLoadBalancer` | Uniform random selection |
| `RoundRobinLoadBalancer` | Strict round-robin |
| `WeightedRandomLoadBalancer` | Weighted random with O(1) selection |
| `WeightedRoundRobinLoadBalancer` | Smooth weighted round-robin (Nginx-style) |

### Error Handling

Cobal classifies errors for intelligent retry decisions:

```kotlin
// Retriable errors (will retry on different node)
// - RateLimitError (429)
// - ServerError (5xx)
// - TimeoutError
// - NetworkError

// Non-retriable errors (short-circuit)
// - AuthenticationError (401/403)
// - InvalidRequestError (400)
```

## Request Flow

```
LoadBalancedModel.chat(request)
  → LoadBalancer.execute() {
      → choose() - Filter available nodes & select via algorithm
      → tryAcquirePermission() - Check circuit breaker state
      → model.chat(request)
        → Success → record metrics
        → Failure → convert error → record failure → decide retry?
      → All attempts exhausted → throw AllNodesUnavailableError
    }
```

## License

The Apache Software License, Version 2.0 - [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt)
