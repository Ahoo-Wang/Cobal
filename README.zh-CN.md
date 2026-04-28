# Cobal

基于 Resilience4j 熔断器的 LLM/AI 客户端 SDK 负载均衡库。

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://github.com/Ahoo-Wang/Cobal/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/me.ahoo.cobal/cobal-core)](https://central.sonatype.com/artifact/me.ahoo.cobal/cobal-core)
[![codecov](https://codecov.io/gh/Ahoo-Wang/Cobal/graph/badge.svg?token=PQhmX3mgo4)](https://codecov.io/gh/Ahoo-Wang/Cobal)

## 概述

Cobal 将 API 请求分发到多个 LLM 端点，透明地处理速率限制。每个端点可以使用独立的 API Key，非常适合 **SAAS 平台中租户提供自有 API Key** 并需要按租户创建负载均衡器实例的场景。

## 特性

- **Kotlin DSL**：流式、类型安全的 DSL 构建负载均衡器
- **多种负载均衡算法**：随机、轮询、加权随机（基于 Vose 别名法的 O(1) 选择）、平滑加权轮询（Nginx 风格）
- **熔断器集成**：基于 Resilience4j，支持按端点健康状态追踪的容错机制
- **透明重试**：自动重试与可配置的错误处理
- **框架集成**：
  - LangChain4j（`me.ahoo.cobal:langchain4j`）
  - Spring AI（`me.ahoo.cobal:spring-ai`）
- **类型安全的错误处理**：层次化的错误模型，区分可重试/不可重试错误
- **租户级实例**：线程安全的注册表，支持按租户缓存负载均衡器

## 模块

```
core  ── 框架无关的抽象层（Node、LoadBalancer、算法、DSL）
  │
  ├── langchain4j  ── LangChain4j 集成（Chat、Streaming、Embedding、Image、Audio）
  └── spring-ai    ── Spring AI 集成（Chat、Embedding、Image、Audio）

bom  ── 集中依赖管理的 Bill of Materials
```

## 快速开始

### Gradle 配置

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// core 模块
implementation("me.ahoo.cobal:core:0.2.0")

// LangChain4j 集成
implementation("me.ahoo.cobal:langchain4j:0.2.0")

// Spring AI 集成
implementation("me.ahoo.cobal:spring-ai:0.2.0")

// 或使用 BOM
implementation(platform("me.ahoo.cobal:bom:0.2.0"))
implementation("me.ahoo.cobal:core")
implementation("me.ahoo.cobal:langchain4j")
```

### DSL 用法（推荐）

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

#### 自定义熔断器

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

#### 算法选择

| DSL 函数 | 算法 | 说明 |
|---|---|---|
| `weightedRoundRobin()`（默认） | `WeightedRoundRobinLoadBalancer` | 平滑加权轮询（Nginx 风格） |
| `roundRobin()` | `RoundRobinLoadBalancer` | 严格轮询 |
| `random()` | `RandomLoadBalancer` | 均匀随机选择 |
| `weightedRandom()` | `WeightedRandomLoadBalancer` | 加权随机，O(1) 选择 |

### 手动构建

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

### 支持的算法

| 算法 | 说明 |
|------|------|
| `RoundRobinLoadBalancer` | 严格轮询 |
| `RandomLoadBalancer` | 均匀随机选择 |
| `WeightedRandomLoadBalancer` | 加权随机，O(1) 选择 |
| `WeightedRoundRobinLoadBalancer` | 平滑加权轮询（Nginx 风格） |

### 错误处理

Cobal 对错误进行分类，用于智能重试决策：

```kotlin
// 可重试错误（会在不同节点上重试）
// - RateLimitError（429）
// - ServerError（5xx）
// - TimeoutError
// - NetworkError

// 不可重试错误（立即短路）
// - AuthenticationError（401/403）
// - InvalidRequestError（400）
```

## 请求流程

```
LoadBalancedModel.chat(request)
  → LoadBalancer.execute() {
      → choose() - 过滤可用节点并通过算法选择
      → tryAcquirePermission() - 检查熔断器状态
      → model.chat(request)
        → 成功 → 记录指标
        → 失败 → 转换错误 → 记录失败 → 决定是否重试？
      → 所有尝试耗尽 → 抛出 AllNodesUnavailableError
    }
```

## 许可证

Apache License 2.0 - [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt)
