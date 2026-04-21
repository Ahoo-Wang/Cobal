# Cobal 负载均衡 SDK 实施计划

> **给 Agent 使用：** 建议使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 来逐步实施本计划。每个步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在三个模块中实现 Cobal 负载均衡 SDK：core（NodeState、ErrorCategory、LoadBalancer 算法、LoadBalancerRegistry）、langchain4j（ModelNode 实现、装饰器、FailurePolicy）、spring-ai（相同模式）。

**架构：** Core 模块定义纯抽象和算法，不依赖任何 LLM 框架。集成模块（langchain4j、spring-ai）各自提供框架特定的 ModelNode 实现、装饰器代理和 FailurePolicy。所有状态管理（available/unavailable/circuit-open）都在 NodeState 中，而不是 Node 中。

**技术栈：** Kotlin 2.3.20、kotlinx-coroutines 1.10.2、JUnit 6、MockK、fluent-assert、LangChain4j 1.13.0、Spring Boot 4.0.5

---

## 第一阶段：Core 模块

### 任务 1：重写 Node.kt — 移除 WatchableNode、更新 Node 接口

**文件：**
- 修改：`core/src/main/kotlin/me/ahoo/cobal/Node.kt`
- 删除：`core/src/test/kotlin/me/ahoo/cobal/experiment/FlowTest.kt`（过时的实验文件）

- [ ] **步骤 1：编写 ModelNode 的失败测试**

```kotlin
// core/src/test/kotlin/me/ahoo/cobal/NodeTest.kt
package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class NodeTest {
    @Test
    fun `ModelNode should hold model instance`() {
        val mockModel = Any()
        val modelNode = object : ModelNode<Any> {
            override val id: NodeId = "test-node"
            override val weight: Int = 1
            override val model: Any = mockModel
        }
        modelNode.id.assert().isEqualTo("test-node")
        modelNode.weight.assert().isEqualTo(1)
        modelNode.model.assert().isSameAs(mockModel)
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.NodeTest" -v`
预期：FAIL — `ModelNode` 尚未定义

- [ ] **步骤 3：用更新后的接口重写 Node.kt**

```kotlin
// core/src/main/kotlin/me/ahoo/cobal/Node.kt
package me.ahoo.cobal

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

- [ ] **步骤 4：运行测试验证它通过**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.NodeTest" -v`
预期：PASS

- [ ] **步骤 5：删除过时的 FlowTest.kt**

- [ ] **步骤 6：提交**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/Node.kt
git rm core/src/test/kotlin/me/ahoo/cobal/experiment/FlowTest.kt
git commit -m "refactor(core): remove WatchableNode, add ModelNode interface"
```

---

### 任务 2：更新 LoadBalancer.kt — 将 choose() 返回类型改为 NodeState

**文件：**
- 修改：`core/src/main/kotlin/me/ahoo/cobal/LoadBalancer.kt`

- [ ] **步骤 1：编写失败测试**

```kotlin
// core/src/test/kotlin/me/ahoo/cobal/LoadBalancerTest.kt
package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class SimpleNode(override val id: NodeId, override val weight: Int = 1) : Node

class LoadBalancerTest {
    @Test
    fun `LoadBalancer choose should return NodeState`() {
        val node = SimpleNode("node-1")
        val loadBalancer = object : LoadBalancer<SimpleNode> {
            override val id: LoadBalancerId = "test-lb"
            override val nodes: List<SimpleNode> = listOf(node)
            override fun choose(): NodeState<SimpleNode> = DefaultNodeState(node)
        }
        val selected = loadBalancer.choose()
        selected.node.id.assert().isEqualTo("node-1")
        selected.available.assert().isTrue()
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.LoadBalancerTest" -v`
预期：FAIL — `NodeState`、`DefaultNodeState`、`LoadBalancerId` 尚未定义

- [ ] **步骤 3：更新 LoadBalancer.kt**

```kotlin
// core/src/main/kotlin/me/ahoo/cobal/LoadBalancer.kt
package me.ahoo.cobal

typealias LoadBalancerId = String

interface LoadBalancer<NODE : Node> {
    val id: LoadBalancerId
    val nodes: List<NODE>
    fun choose(): NodeState<NODE>
}
```

- [ ] **步骤 4：运行测试验证它失败**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.LoadBalancerTest" -v`
预期：FAIL — `NodeState`、`DefaultNodeState` 尚未定义（符合预期，还未创建）

- [ ] **步骤 5：提交**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/LoadBalancer.kt
git commit -m "refactor(core): LoadBalancer.choose() returns NodeState instead of NODE"
```

---

### 任务 3：创建 NodeState.kt — NodeStatus、NodeEvent、ErrorCategory、NodeError、NodeFailurePolicy、NodeState、DefaultNodeState

**文件：**
- 创建：`core/src/main/kotlin/me/ahoo/cobal/NodeState.kt`
- 测试：`core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt`

- [ ] **步骤 1：编写 NodeStatus 和 NodeState 的失败测试**

```kotlin
// core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt
package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SimpleNode(override val id: NodeId) : Node

class NodeStateTest {

    @Test
    fun `available node should have AVAILABLE status`() {
        val node = SimpleNode("node-1")
        val nodeState = DefaultNodeState(node)
        nodeState.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        nodeState.available.assert().isTrue()
    }

    @Test
    fun `onFailure with RATE_LIMITED should mark node UNAVAILABLE`() {
        val node = SimpleNode("node-1")
        val nodeState = DefaultNodeState(node, circuitOpenThreshold = 3)
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        nodeState.available.assert().isFalse()
    }

    @Test
    fun `onFailure with INVALID_REQUEST should not change status`() {
        val node = SimpleNode("node-1")
        val nodeState = DefaultNodeState(node)
        val error = NodeError(ErrorCategory.INVALID_REQUEST, RuntimeException("400"))
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        nodeState.available.assert().isTrue()
    }

    @Test
    fun `consecutive failures should trigger CIRCUIT_OPEN`() {
        val node = SimpleNode("node-1")
        val nodeState = DefaultNodeState(node, circuitOpenThreshold = 2)
        val error = NodeError(ErrorCategory.SERVER_ERROR, RuntimeException("500"))
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
    }

    @Test
    fun `node should auto-recover when recoverAt is passed`() {
        val node = SimpleNode("node-1")
        val nodeState = DefaultNodeState(node)
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        nodeState.onFailure(error)
        nodeState.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        // 在 recoverAt 之后访问 status 应该触发自动恢复
        val status = nodeState.status
        status.assert().isEqualTo(NodeStatus.AVAILABLE)
        nodeState.available.assert().isTrue()
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.NodeStateTest" -v`
预期：FAIL — `NodeState`、`DefaultNodeState`、`NodeStatus`、`NodeEvent`、`NodeError`、`ErrorCategory`、`NodeFailureDecision`、`NodeFailurePolicy` 尚未定义

- [ ] **步骤 3：编写完整的 NodeState.kt**

```kotlin
// core/src/main/kotlin/me/ahoo/cobal/NodeState.kt
package me.ahoo.cobal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Clock
import java.time.Instant

enum class NodeStatus {
    AVAILABLE,
    UNAVAILABLE,
    CIRCUIT_OPEN
}

sealed interface NodeEvent {
    val nodeId: NodeId
    data class MarkedUnavailable(override val nodeId: NodeId, val recoverAt: Instant) : NodeEvent
    data class Recovered(override val nodeId: NodeId) : NodeEvent
}

enum class ErrorCategory {
    RATE_LIMITED,
    SERVER_ERROR,
    AUTHENTICATION,
    INVALID_REQUEST,
    TIMEOUT,
    NETWORK
}

class NodeError(
    val category: ErrorCategory,
    override val cause: Throwable
) : Exception(cause?.message, cause)

data class NodeFailureDecision(val recoverAt: Instant)

fun interface NodeFailurePolicy {
    fun evaluate(error: NodeError): NodeFailureDecision?

    companion object {
        val Default = NodeFailurePolicy { null }
    }
}

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
    private val circuitOpenThreshold: Int = 5,
    private val clock: Clock = Clock.systemUTC()
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
                currentRecoverAt != null && currentRecoverAt.isAfter(clock.instant()) -> NodeStatus.UNAVAILABLE
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

- [ ] **步骤 4：运行测试验证它通过**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.NodeStateTest" -v`
预期：PASS

- [ ] **步骤 5：提交**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/NodeState.kt
git add core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt
git commit -m "feat(core): add NodeState, NodeStatus, NodeEvent, ErrorCategory, NodeError, NodeFailurePolicy"
```

---

### 任务 4：创建 algorithm/RandomLoadBalancer.kt

**文件：**
- 创建：`core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt`
- 创建：`core/src/test/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancerTest.kt`

- [ ] **步骤 1：编写失败测试**

```kotlin
// core/src/test/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancerTest.kt
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.*
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class SimpleNode(override val id: NodeId, override val weight: Int = 1) : Node

class RandomLoadBalancerTest {
    @Test
    fun `choose should return available node`() {
        val node1 = SimpleNode("node-1")
        val node2 = SimpleNode("node-2")
        val lb = RandomLoadBalancer("random-lb", listOf(node1, node2))
        val chosen = lb.choose()
        listOf("node-1", "node-2").assert().contains(chosen.node.id)
    }

    @Test
    fun `choose should skip unavailable node`() {
        val node1 = SimpleNode("node-1")
        val node2 = SimpleNode("node-2")
        val lb = RandomLoadBalancer("random-lb", listOf(node1, node2))
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        val selected = lb.choose()
        selected.onFailure(error)
        val chosen2 = lb.choose()
        chosen2.node.id.assert().isEqualTo("node-2")
    }

    @Test
    fun `all nodes unavailable should throw`() {
        val node1 = SimpleNode("node-1")
        val lb = RandomLoadBalancer("random-lb", listOf(node1))
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        val selected = lb.choose()
        selected.onFailure(error)
        org.junit.jupiter.api.assertThrows<AllNodesUnavailableError> {
            lb.choose()
        }
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.algorithm.RandomLoadBalancerTest" -v`
预期：FAIL — `RandomLoadBalancer`、`AllNodesUnavailableError` 尚未定义

- [ ] **步骤 3：编写 RandomLoadBalancer.kt**

```kotlin
// core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.*
import java.util.concurrent.ThreadLocalRandom

class AllNodesUnavailableError(val loadBalancerId: LoadBalancerId) : RuntimeException(
    "All nodes unavailable in load balancer: $loadBalancerId"
)

class RandomLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val nodes: List<NODE>
) : LoadBalancer<NODE> {

    private val nodeStates: Map<NodeId, NodeState<NODE>> = nodes.associate {
        it.id to DefaultNodeState(it)
    }

    private fun availableNodes(): List<NodeState<NODE>> {
        return nodeStates.values.filter { it.available }
    }

    override fun choose(): NodeState<NODE> {
        val available = availableNodes()
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        return available[ThreadLocalRandom.current().nextInt(available.size)]
    }
}
```

- [ ] **步骤 4：运行测试验证它通过**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.algorithm.RandomLoadBalancerTest" -v`
预期：PASS

- [ ] **步骤 5：提交**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt
git add core/src/test/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancerTest.kt
git commit -m "feat(core): add RandomLoadBalancer with AllNodesUnavailableError"
```

---

### 任务 5：创建 algorithm/RoundRobinLoadBalancer.kt

**文件：**
- 创建：`core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt`
- 创建：`core/src/test/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancerTest.kt`

- [ ] **步骤 1：编写失败测试**

```kotlin
// core/src/test/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancerTest.kt
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.*
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class RoundRobinLoadBalancerTest {
    @Test
    fun `choose should rotate through nodes in order`() {
        val node1 = SimpleNode("node-1")
        val node2 = SimpleNode("node-2")
        val lb = RoundRobinLoadBalancer("rr-lb", listOf(node1, node2))
        lb.choose().node.id.assert().isEqualTo("node-1")
        lb.choose().node.id.assert().isEqualTo("node-2")
        lb.choose().node.id.assert().isEqualTo("node-1")
    }

    @Test
    fun `choose should skip unavailable node`() {
        val node1 = SimpleNode("node-1")
        val node2 = SimpleNode("node-2")
        val lb = RoundRobinLoadBalancer("rr-lb", listOf(node1, node2))
        val error = NodeError(ErrorCategory.RATE_LIMITED, RuntimeException("429"))
        lb.choose() // node-1
        val selected2 = lb.choose() // node-2
        selected2.onFailure(error) // mark node-2 unavailable
        lb.choose().node.id.assert().isEqualTo("node-1") // back to node-1
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.algorithm.RoundRobinLoadBalancerTest" -v`
预期：FAIL — `RoundRobinLoadBalancer` 尚未定义

- [ ] **步骤 3：编写 RoundRobinLoadBalancer.kt**

```kotlin
// core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.*
import java.util.concurrent.atomic.AtomicInteger

class RoundRobinLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val nodes: List<NODE>
) : LoadBalancer<NODE> {

    private val nodeStates: Map<NodeId, NodeState<NODE>> = nodes.associate {
        it.id to DefaultNodeState(it)
    }

    private val index = AtomicInteger(0)

    override fun choose(): NodeState<NODE> {
        val available = nodeStates.values.filter { it.available }
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        val startIndex = index.getAndIncrement() % available.size
        return available[startIndex]
    }
}
```

- [ ] **步骤 4：运行测试验证它通过**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.algorithm.RoundRobinLoadBalancerTest" -v`
预期：PASS

- [ ] **步骤 5：提交**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt
git add core/src/test/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancerTest.kt
git commit -m "feat(core): add RoundRobinLoadBalancer"
```

---

### 任务 6：创建 algorithm/WeightedRoundRobinLoadBalancer.kt

**文件：**
- 创建：`core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt`
- 创建：`core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancerTest.kt`

- [ ] **步骤 1：编写失败测试**

```kotlin
// core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancerTest.kt
package me.ahoo.cobal.algorithm

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class WeightedRoundRobinLoadBalancerTest {
    @Test
    fun `weighted round robin should respect node weights`() {
        val node1 = SimpleNode("node-1", weight = 3)
        val node2 = SimpleNode("node-2", weight = 1)
        val lb = WeightedRoundRobinLoadBalancer("wrr-lb", listOf(node1, node2))
        // node-1 在 4 次一轮循环中应被选中 3 次，node-2 选中 1 次
        val counts = mutableMapOf("node-1" to 0, "node-2" to 0)
        repeat(12) { // 3 轮循环
            counts[lb.choose().node.id] = counts[lb.choose().node.id]!! + 1
        }
        counts["node-1"].assert().isEqualTo(9) // 3 * 3
        counts["node-2"].assert().isEqualTo(3)  // 3 * 1
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRoundRobinLoadBalancerTest" -v`
预期：FAIL — `WeightedRoundRobinLoadBalancer` 尚未定义

- [ ] **步骤 3：编写 WeightedRoundRobinLoadBalancer.kt**

```kotlin
// core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.*

class WeightedRoundRobinLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val nodes: List<NODE>
) : LoadBalancer<NODE> {

    private val nodeStates: Map<NodeId, NodeState<NODE>> = nodes.associate {
        it.id to DefaultNodeState(it)
    }

    private var currentIndex = 0
    private var currentWeight = 0
    private val maxWeight: Int = nodes.maxOf { it.weight }
    private val weightMap: Map<NodeId, Int> = nodes.associate { it.id to it.weight }

    override fun choose(): NodeState<NODE> {
        val available = nodeStates.values.filter { it.available }
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }

        while (true) {
            currentIndex = (currentIndex + 1) % available.size
            if (currentIndex == 0) {
                currentWeight--
                if (currentWeight <= 0) {
                    currentWeight = maxWeight
                }
            }
            val candidate = available[currentIndex]
            if (weightMap[candidate.node.id]!! >= currentWeight) {
                return candidate
            }
        }
    }
}
```

- [ ] **步骤 4：运行测试验证它通过**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRoundRobinLoadBalancerTest" -v`
预期：PASS

- [ ] **步骤 5：提交**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt
git add core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancerTest.kt
git commit -m "feat(core): add WeightedRoundRobinLoadBalancer"
```

---

### 任务 7：创建 LoadBalancerRegistry.kt

**文件：**
- 创建：`core/src/main/kotlin/me/ahoo/cobal/LoadBalancerRegistry.kt`
- 创建：`core/src/test/kotlin/me/ahoo/cobal/LoadBalancerRegistryTest.kt`

- [ ] **步骤 1：编写失败测试**

```kotlin
// core/src/test/kotlin/me/ahoo/cobal/LoadBalancerRegistryTest.kt
package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class LoadBalancerRegistryTest {
    @Test
    fun `getOrCreate should create and cache instance`() {
        val registry = LoadBalancerRegistry()
        val lb = registry.getOrCreate("lb-1") {
            RandomLoadBalancer("lb-1", listOf(SimpleNode("node-1")))
        }
        val lb2 = registry.getOrCreate("lb-1") { throw RuntimeException("should not be called") }
        lb.assert().isSameAs(lb2)
    }

    @Test
    fun `remove should evict cached instance`() {
        val registry = LoadBalancerRegistry()
        val lb = registry.getOrCreate("lb-1") {
            RandomLoadBalancer("lb-1", listOf(SimpleNode("node-1")))
        }
        registry.remove("lb-1")
        registry.contains("lb-1").assert().isFalse()
    }

    @Test
    fun `contains should return true for existing id`() {
        val registry = LoadBalancerRegistry()
        registry.getOrCreate("lb-1") {
            RandomLoadBalancer("lb-1", listOf(SimpleNode("node-1")))
        }
        registry.contains("lb-1").assert().isTrue()
        registry.contains("lb-2").assert().isFalse()
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.LoadBalancerRegistryTest" -v`
预期：FAIL — `LoadBalancerRegistry` 尚未定义

- [ ] **步骤 3：编写 LoadBalancerRegistry.kt**

```kotlin
// core/src/main/kotlin/me/ahoo/cobal/LoadBalancerRegistry.kt
package me.ahoo.cobal

class LoadBalancerRegistry {
    private val registry = java.util.concurrent.ConcurrentHashMap<LoadBalancerId, LoadBalancer<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <NODE : Node> getOrCreate(id: LoadBalancerId, factory: () -> LoadBalancer<NODE>): LoadBalancer<NODE> {
        registry[id]?.let { return it as LoadBalancer<NODE> }
        return synchronized(this) {
            registry[id]?.let { return@synchronized it as LoadBalancer<NODE> }
            factory().also { registry[id] = it }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <NODE : Node> get(id: LoadBalancerId): LoadBalancer<NODE>? {
        return registry[id] as LoadBalancer<NODE>?
    }

    fun remove(id: LoadBalancerId): LoadBalancer<*>? {
        return registry.remove(id)
    }

    fun contains(id: LoadBalancerId): Boolean {
        return registry.containsKey(id)
    }
}
```

- [ ] **步骤 4：运行测试验证它通过**

运行：`./gradlew :core:test --tests "me.ahoo.cobal.LoadBalancerRegistryTest" -v`
预期：PASS

- [ ] **步骤 5：提交**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/LoadBalancerRegistry.kt
git add core/src/test/kotlin/me/ahoo/cobal/LoadBalancerRegistryTest.kt
git commit -m "feat(core): add LoadBalancerRegistry for application-level caching"
```

---

### 任务 8：Core 模块最终验证

**文件：** 所有 core 模块文件

- [ ] **步骤 1：运行所有 core 测试**

运行：`./gradlew :core:test`
预期：全部通过

- [ ] **步骤 2：运行 detekt 自动修复**

运行：`./gradlew :core:detekt --auto-correct`
预期：Clean（或自动修复的问题）

- [ ] **步骤 3：提交所有剩余 core 更改**

```bash
git add -A
git commit -m "feat(core): complete core module implementation"
```

---

## 第二阶段：LangChain4j 模块

### 任务 9：LangChain4j ModelNode 实现

**文件：**
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/ChatModelNode.kt`
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/EmbeddingModelNode.kt`
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/ImageModelNode.kt`
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/AudioTranscriptionModelNode.kt`
- 创建：`langchain4j/src/test/kotlin/me/ahoo/cobal/langchain4j/model/ModelNodesTest.kt`

- [ ] **步骤 1：编写失败测试**

```kotlin
// langchain4j/src/test/kotlin/me/ahoo/cobal/langchain4j/model/ModelNodesTest.kt
package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.chat.ChatLanguageModel
import me.ahoo.cobal.*
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class ChatModelNodeTest {
    @Test
    fun `ChatModelNode should hold ChatLanguageModel`() {
        val mockModel = ChatLanguageModel { "response" }
        val node = ChatModelNode("model-1", weight = 2, model = mockModel)
        node.id.assert().isEqualTo("model-1")
        node.weight.assert().isEqualTo(2)
        node.model.assert().isNotNull()
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :langchain4j:test --tests "me.ahoo.cobal.langchain4j.model.ModelNodesTest" -v`
预期：FAIL — `ChatModelNode` 尚未定义

- [ ] **步骤 3：编写 ChatModelNode.kt**

```kotlin
// langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/ChatModelNode.kt
package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.chat.ChatLanguageModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class ChatModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: ChatLanguageModel
) : ModelNode<ChatLanguageModel>
```

- [ ] **步骤 4：编写 EmbeddingModelNode.kt、ImageModelNode.kt、AudioTranscriptionModelNode.kt**（每个模型类型相同模式）

```kotlin
// langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/EmbeddingModelNode.kt
package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.embedding.EmbeddingModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class EmbeddingModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: EmbeddingModel
) : ModelNode<EmbeddingModel>

// langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/ImageModelNode.kt
package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.image.ImageModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class ImageModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: ImageModel
) : ModelNode<ImageModel>

// langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/AudioTranscriptionModelNode.kt
package me.ahoo.cobal.langchain4j.model

import dev.langchain4j.model.audio.AudioTranscriptionModel
import me.ahoo.cobal.ModelNode
import me.ahoo.cobal.NodeId

class AudioTranscriptionModelNode(
    override val id: NodeId,
    override val weight: Int = 1,
    override val model: AudioTranscriptionModel
) : ModelNode<AudioTranscriptionModel>
```

- [ ] **步骤 5：运行测试验证它通过**

运行：`./gradlew :langchain4j:test --tests "me.ahoo.cobal.langchain4j.model.ModelNodesTest" -v`
预期：PASS

- [ ] **步骤 6：提交**

```bash
git add langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/model/
git add langchain4j/src/test/kotlin/me/ahoo/cobal/langchain4j/model/
git commit -m "feat(langchain4j): add ModelNode implementations for Chat, Embedding, Image, Audio models"
```

---

### 任务 10：LangChain4jFailurePolicy 和 LoadBalancedChatModel

**文件：**
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LangChain4jFailurePolicy.kt`
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedChatModel.kt`
- 创建：`langchain4j/src/test/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedChatModelTest.kt`

- [ ] **步骤 1：编写 LangChain4jFailurePolicy 的失败测试**

```kotlin
// langchain4j/src/test/kotlin/me/ahoo/cobal/langchain4j/LangChain4jFailurePolicyTest.kt
package me.ahoo.cobal.langchain4j

import dev.langchain4j.exception.RateLimitException
import me.ahoo.cobal.*
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration

class LangChain4jFailurePolicyTest {
    @Test
    fun `RATE_LIMITED should return decision with recoverAt`() {
        val error = NodeError(ErrorCategory.RATE_LIMITED, RateLimitException("429", null, null, Duration.ofSeconds(60)))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNotNull()
        decision!!.recoverAt.assert().isAfter(Instant.now())
    }

    @Test
    fun `AUTHENTICATION should return long recoverAt`() {
        val error = NodeError(ErrorCategory.AUTHENTICATION, RuntimeException("401"))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNotNull()
        decision!!.recoverAt.assert().isAfter(Instant.now().plus(Duration.ofMinutes(30)))
    }

    @Test
    fun `SERVER_ERROR should return null (no state change)`() {
        val error = NodeError(ErrorCategory.SERVER_ERROR, RuntimeException("500"))
        val decision = LangChain4jFailurePolicy.evaluate(error)
        decision.assert().isNull()
    }
}
```

- [ ] **步骤 2：运行测试验证它失败**

运行：`./gradlew :langchain4j:test --tests "me.ahoo.cobal.langchain4j.LangChain4jFailurePolicyTest" -v`
预期：FAIL — `LangChain4jFailurePolicy` 尚未定义

- [ ] **步骤 3：编写 LangChain4jFailurePolicy.kt**

```kotlin
// langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LangChain4jFailurePolicy.kt
package me.ahoo.cobal.langchain4j

import dev.langchain4j.exception.RateLimitException
import me.ahoo.cobal.*
import java.time.Duration
import java.time.Instant

val LangChain4jFailurePolicy = NodeFailurePolicy { error ->
    when (error.category) {
        ErrorCategory.RATE_LIMITED -> {
            val retryAfter = when (val cause = error.cause) {
                is RateLimitException -> cause.retryAfter
                else -> null
            }
            NodeFailureDecision(
                recoverAt = Instant.now() + (retryAfter ?: Duration.ofSeconds(30))
            )
        }
        ErrorCategory.AUTHENTICATION -> NodeFailureDecision(
            recoverAt = Instant.now() + Duration.ofHours(1)
        )
        else -> null
    }
}
```

- [ ] **步骤 4：运行测试验证它通过**

运行：`./gradlew :langchain4j:test --tests "me.ahoo.cobal.langchain4j.LangChain4jFailurePolicyTest" -v`
预期：PASS

- [ ] **步骤 5：编写 LoadBalancedChatModel 的失败测试**

```kotlin
// langchain4j/src/test/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedChatModelTest.kt
package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ChatResponse
import me.ahoo.cobal.langchain4j.model.ChatModelNode
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LoadBalancedChatModelTest {
    @Test
    fun `should use load balancer to choose node`() {
        var callCount = 0
        val mockModel = ChatLanguageModel { callCount++; ChatResponse.from("OK") }
        val node = ChatModelNode("node-1", model = mockModel)
        val lb = RandomLoadBalancer("lb", listOf(node))
        val lbChat = LoadBalancedChatModel(lb, maxRetries = 1)
        val response = lbChat.chat(ChatRequest.builder().input("Hi").build())
        response.assert().isNotNull()
        callCount.assert().isEqualTo(1)
    }

    @Test
    fun `should retry on failure and mark node unavailable`() {
        var callCount = 0
        val mockModel = ChatLanguageModel {
            callCount++
            throw RuntimeException("error")
        }
        val node1 = ChatModelNode("node-1", model = mockModel)
        val node2 = ChatModelNode("node-2", model = ChatLanguageModel { ChatResponse.from("OK") })
        val lb = RandomLoadBalancer("lb", listOf(node1, node2))
        val lbChat = LoadBalancedChatModel(lb, maxRetries = 2)
        val response = lbChat.chat(ChatRequest.builder().input("Hi").build())
        response.assert().isNotNull()
        callCount.assert().isEqualTo(1) // node-1 被标记不可用，重试在 node-2 上成功
    }

    @Test
    fun `should throw AllNodesUnavailableError when all nodes fail`() {
        val failingModel = ChatLanguageModel { throw RuntimeException("error") }
        val node = ChatModelNode("node-1", model = failingModel)
        val lb = RandomLoadBalancer("lb", listOf(node))
        val lbChat = LoadBalancedChatModel(lb, maxRetries = 1)
        assertThrows<AllNodesUnavailableError> {
            lbChat.chat(ChatRequest.builder().input("Hi").build())
        }
    }
}
```

- [ ] **步骤 6：运行测试验证它失败**

运行：`./gradlew :langchain4j:test --tests "me.ahoo.cobal.langchain4j.LoadBalancedChatModelTest" -v`
预期：FAIL — `LoadBalancedChatModel` 尚未定义

- [ ] **步骤 7：编写 LoadBalancedChatModel.kt**

```kotlin
// langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedChatModel.kt
package me.ahoo.cobal.langchain4j

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ChatResponse
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.langchain4j.model.ChatModelNode

class LoadBalancedChatModel(
    private val loadBalancer: LoadBalancer<ChatModelNode>,
    private val maxRetries: Int = 3
) : ChatLanguageModel {

    override fun chat(request: ChatRequest): ChatResponse {
        repeat(maxRetries) {
            val selected = loadBalancer.choose()
            try {
                return selected.node.model.chat(request)
            } catch (e: Exception) {
                val nodeError = toNodeError(e)
                selected.onFailure(nodeError)
            }
        }
        throw AllNodesUnavailableError(loadBalancer.id)
    }

    companion object {
        fun toNodeError(e: Exception): NodeError {
            val category = when (e) {
                is dev.langchain4j.exception.RateLimitException -> ErrorCategory.RATE_LIMITED
                is dev.langchain4j.exception.TokenLimitExceededException -> ErrorCategory.INVALID_REQUEST
                else -> ErrorCategory.SERVER_ERROR
            }
            return NodeError(category, e)
        }
    }
}
```

- [ ] **步骤 8：运行测试验证它通过**

运行：`./gradlew :langchain4j:test --tests "me.ahoo.cobal.langchain4j.LoadBalancedChatModelTest" -v`
预期：PASS

- [ ] **步骤 9：提交**

```bash
git add langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/
git add langchain4j/src/test/kotlin/me/ahoo/cobal/langchain4j/
git commit -m "feat(langchain4j): add LangChain4jFailurePolicy and LoadBalancedChatModel"
```

---

### 任务 11：LangChain4j 剩余装饰器

**文件：**
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedStreamingChatModel.kt`
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedEmbeddingModel.kt`
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedImageModel.kt`
- 创建：`langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedAudioTranscriptionModel.kt`

- [ ] **步骤 1：为每个装饰器编写测试和实现**（遵循 LoadBalancedChatModel 相同模式）

**LoadBalancedStreamingChatModel** — 代理 `StreamingChatLanguageModel`
**LoadBalancedEmbeddingModel** — 代理 `EmbeddingModel`
**LoadBalancedImageModel** — 代理 `ImageModel`
**LoadBalancedAudioTranscriptionModel** — 代理 `AudioTranscriptionModel`

- [ ] **步骤 2：为每个装饰器运行测试**

运行：`./gradlew :langchain4j:test --tests "me.ahoo.cobal.langchain4j.LoadBalanced*" -v`
预期：全部通过

- [ ] **步骤 3：提交**

```bash
git add langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/
git commit -m "feat(langchain4j): add remaining load-balanced model decorators"
```

---

### 任务 12：LangChain4j 模块最终验证

- [ ] **步骤 1：运行所有 langchain4j 测试**

运行：`./gradlew :langchain4j:test`
预期：全部通过

- [ ] **步骤 2：运行 detekt 自动修复**

运行：`./gradlew :langchain4j:detekt --auto-correct`

- [ ] **步骤 3：提交**

```bash
git add -A
git commit -m "feat(langchain4j): complete langchain4j module implementation"
```

---

## 第三阶段：Spring AI 模块

### 任务 13：Spring AI ModelNode 实现

**文件：**
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/model/ChatModelNode.kt`
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/model/EmbeddingModelNode.kt`
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/model/ImageModelNode.kt`
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/model/AudioTranscriptionModelNode.kt`
- 创建：`spring-ai/src/test/kotlin/me/ahoo/cobal/springai/model/ModelNodesTest.kt`

- [ ] **步骤 1：遵循任务 9 的相同模式，但使用 Spring AI 接口**（`org.springframework.ai.chat.model.ChatModel` 等）

- [ ] **步骤 2：运行测试**

运行：`./gradlew :spring-ai:test`
预期：全部通过

- [ ] **步骤 3：提交**

```bash
git add spring-ai/src/main/kotlin/me/ahoo/cobal/springai/model/
git add spring-ai/src/test/kotlin/me/ahoo/cobal/springai/model/
git commit -m "feat(spring-ai): add ModelNode implementations for Chat, Embedding, Image, Audio models"
```

---

### 任务 14：SpringAiFailurePolicy 和 LoadBalancedChatModel

**文件：**
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/SpringAiFailurePolicy.kt`
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedChatModel.kt`
- 创建：`spring-ai/src/test/kotlin/me/ahoo/cobal/springai/`

- [ ] **步骤 1：遵循任务 10 的相同模式，但使用 Spring AI**

- [ ] **步骤 2：运行测试**

运行：`./gradlew :spring-ai:test`
预期：全部通过

- [ ] **步骤 3：提交**

```bash
git add spring-ai/src/main/kotlin/me/ahoo/cobal/springai/
git add spring-ai/src/test/kotlin/me/ahoo/cobal/springai/
git commit -m "feat(spring-ai): add SpringAiFailurePolicy and LoadBalancedChatModel"
```

---

### 任务 15：Spring AI 剩余装饰器

**文件：**
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedEmbeddingModel.kt`
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedImageModel.kt`
- 创建：`spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedAudioTranscriptionModel.kt`

- [ ] **步骤 1：遵循任务 11 的相同模式，但使用 Spring AI**

- [ ] **步骤 2：运行测试**

运行：`./gradlew :spring-ai:test`
预期：全部通过

- [ ] **步骤 3：提交**

```bash
git add spring-ai/src/main/kotlin/me/ahoo/cobal/springai/
git commit -m "feat(spring-ai): add remaining load-balanced model decorators"
```

---

### 任务 16：Spring AI 模块最终验证

- [ ] **步骤 1：运行所有 spring-ai 测试**

运行：`./gradlew :spring-ai:test`
预期：全部通过

- [ ] **步骤 2：运行 detekt 自动修复**

运行：`./gradlew :spring-ai:detekt --auto-correct`

- [ ] **步骤 3：提交**

```bash
git add -A
git commit -m "feat(spring-ai): complete spring-ai module implementation"
```

---

## 第四阶段：全项目验证

### 任务 17：完整构建、测试和覆盖率

- [ ] **步骤 1：运行完整构建**

运行：`./gradlew build`
预期：全部通过（构建、测试、detekt）

- [ ] **步骤 2：生成并验证覆盖率**

运行：`./gradlew koverMergedVerify`
预期：通过（达到覆盖率阈值）

- [ ] **步骤 3：生成文档**

运行：`./gradlew :core:dokka`
预期：HTML 文档生成在 `core/build/dokka`

- [ ] **步骤 4：提交**

```bash
git add -A
git commit -m "chore: complete all modules implementation and verification"
```

---

## 需求覆盖检查

| 设计文档章节 | 实现任务 |
|---|---|
| Node 抽象 | 任务 1 |
| ModelNode 桥接 | 任务 1 |
| NodeStatus、NodeEvent | 任务 3 |
| ErrorCategory、NodeError | 任务 3 |
| NodeFailurePolicy | 任务 3 |
| NodeState 接口 + DefaultNodeState | 任务 3 |
| LoadBalancer.choose() 返回 NodeState | 任务 2 |
| RandomLoadBalancer | 任务 4 |
| RoundRobinLoadBalancer | 任务 5 |
| WeightedRoundRobinLoadBalancer | 任务 6 |
| LoadBalancerRegistry | 任务 7 |
| LangChain4j ModelNode + 装饰器 + FailurePolicy | 任务 9-12 |
| Spring AI ModelNode + 装饰器 + FailurePolicy | 任务 13-15 |

---

## 类型一致性检查

- `LoadBalancer.choose()` 返回 `NodeState<NODE>` — 在任务 4、5、6 中保持一致
- `NodeState.onFailure(error: NodeError)` — 在任务 3 中保持一致
- `ErrorCategory.RATE_LIMITED`、`AUTHENTICATION` 等 — 在任务 10、14 中使用
- `AllNodesUnavailableError(loadBalancerId: LoadBalancerId)` — 在任务 4、10、14 中保持一致
- `NodeFailureDecision(recoverAt: Instant)` — 在任务 3 和任务 10 中使用
