# Cobal

Load balancing library for LLM/AI client SDKs with Resilience4j circuit breakers.

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://github.com/Ahoo-Wang/Cobal/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/me.ahoo.cobal/cobal-core)](https://central.sonatype.com/artifact/me.ahoo.cobal/cobal-core)
[![codecov](https://codecov.io/gh/Ahoo-Wang/Cobal/graph/badge.svg?token=PQhmX3mgo4)](https://codecov.io/gh/Ahoo-Wang/Cobal)

## Overview

Cobal distributes API requests across multiple LLM endpoints to handle rate limiting transparently. Each endpoint can have its own API key, making Cobal ideal for **SAAS platforms where tenants provide their own API keys** and need per-tenant load balancer instances.

## Features

- **Kotlin DSL**: Fluent, type-safe DSL for building load balancers
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
core  ── Framework-agnostic abstractions (Node, LoadBalancer, algorithms, DSL)
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
implementation("me.ahoo.cobal:core:0.2.0")

// LangChain4j integration
implementation("me.ahoo.cobal:langchain4j:0.2.0")

// Spring AI integration
implementation("me.ahoo.cobal:spring-ai:0.2.0")

// Or use BOM
implementation(platform("me.ahoo.cobal:bom:0.2.0"))
implementation("me.ahoo.cobal:core")
implementation("me.ahoo.cobal:langchain4j")
```

### DSL Usage (Recommended)

```kotlin
import me.ahoo.cobal.dsl.loadBalancer
import me.ahoo.cobal.langchain4j.LoadBalancedChatModel

val model1 = OpenAiChatModel.builder().apiKey("key1").build()
val model2 = OpenAiChatModel.builder().apiKey("key2").build()

val balancedModel = LoadBalancedChatModel(
    loadBalancer("my-lb") {
        weightedRoundRobin()
        node("node-1", weight = 5) { model(model1) }
        node("node-2", weight = 1) { model(model2) }
    }
)

val response = balancedModel.chat(ChatRequest("Hello!"))
```

#### Custom Circuit Breaker

```kotlin
val lb = loadBalancer<ChatModel>("my-lb") {
    roundRobin()
    node("node-1") {
        model(model1)
        circuitBreaker {
            failureRateThreshold(50f)
            slidingWindowSize(10)
            waitDurationInOpenState(Duration.ofSeconds(30))
        }
    }
    node("node-2") { model(model2) }
}
```

#### Algorithm Selection

| DSL Function | Algorithm | Description |
|---|---|---|
| `weightedRoundRobin()` (default) | `WeightedRoundRobinLoadBalancer` | Smooth weighted round-robin (Nginx-style) |
| `roundRobin()` | `RoundRobinLoadBalancer` | Strict round-robin |
| `random()` | `RandomLoadBalancer` | Uniform random selection |
| `weightedRandom()` | `WeightedRandomLoadBalancer` | Weighted random with O(1) selection |

### Manual Construction

```kotlin
import me.ahoo.cobal.*
import me.ahoo.cobal.algorithm.RoundRobinLoadBalancer
import me.ahoo.cobal.state.DefaultNodeState

val node1 = DefaultModelNode("node-1", weight = 1, model = model1)
val node2 = DefaultModelNode("node-2", weight = 3, model = model2)

val lb = RoundRobinLoadBalancer("my-lb", listOf(
    DefaultNodeState(node1),
    DefaultNodeState(node2),
))

val balancedModel = LoadBalancedChatModel(lb)
```

### Supported Algorithms

| Algorithm | Description |
|-----------|-------------|
| `RoundRobinLoadBalancer` | Strict round-robin |
| `RandomLoadBalancer` | Uniform random selection |
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
