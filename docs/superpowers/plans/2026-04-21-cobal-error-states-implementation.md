# LoadBalancer.states + Exception Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement three coordinated changes: (1) rename LoadBalancer.nodes to states and add availableStates computed property, (2) unify exception hierarchy with RetriableError marker interface and nodeId-aware NodeError, (3) add ErrorConverter interface for langchain4j and spring-ai to convert their exceptions to CobalError

**Architecture:** Exception hierarchy follows standard OOP pattern: CobalError (abstract) → NodeError (nodeId carrier) → concrete errors. LoadBalancer now holds NodeState list directly instead of Node list, with availableStates as a simple filtered view. ErrorConverter allows external modules to plug in their exception conversion logic.

**Tech Stack:** Kotlin, JUnit Jupiter, MockK

---

## File Map

### Core Module
- `core/src/main/kotlin/me/ahoo/cobal/CobalError.kt` - exception hierarchy + ErrorConverter
- `core/src/main/kotlin/me/ahoo/cobal/LoadBalancer.kt` - interface change
- `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt` - ErrorCategory removal, onFailure signature
- `core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt` - states + choose
- `core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt` - states + choose
- `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt` - states + choose

### LangChain4j Module
- `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedChatModel.kt`
- `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedStreamingChatModel.kt`
- `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedEmbeddingModel.kt`
- `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedImageModel.kt`
- `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedAudioTranscriptionModel.kt`

### Spring AI Module
- `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedChatModel.kt`
- `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedEmbeddingModel.kt`
- `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedImageModel.kt`
- `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedAudioTranscriptionModel.kt`

---

## Task 1: Rewrite CobalError.kt

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/CobalError.kt`
- Test: `core/src/test/kotlin/me/ahoo/cobal/CobalErrorTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package me.ahoo.cobal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CobalErrorTest {

    @Test
    fun `CobalError is abstract and extends RuntimeException`() {
        val cause = RuntimeException("original")
        val error = object : CobalError("test", cause) {}
        assertIs<CobalError>(error)
        assertIs<RuntimeException>(error)
        assertEquals("test", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `RetriableError is a marker interface`() {
        val error = RateLimitError(NodeId("node-1"), null)
        assertIs<RetriableError>(error)
        assertIs<NodeError>(error)
        assertEquals(NodeId("node-1"), error.nodeId)
        assertTrue(error.message!!.contains("node-1"))
    }

    @Test
    fun `RateLimitError is retriable`() {
        val error = RateLimitError(NodeId("node-1"), null)
        assertIs<RetriableError>(error)
        assertEquals("Rate limited [node-1]", error.message)
    }

    @Test
    fun `ServerError is retriable`() {
        val error = ServerError(NodeId("node-2"), null)
        assertIs<RetriableError>(error)
        assertEquals("Server error [node-2]", error.message)
    }

    @Test
    fun `TimeoutError is retriable`() {
        val error = TimeoutError(NodeId("node-3"), null)
        assertIs<RetriableError>(error)
        assertEquals("Timeout [node-3]", error.message)
    }

    @Test
    fun `NetworkError is retriable`() {
        val error = NetworkError(NodeId("node-4"), null)
        assertIs<RetriableError>(error)
        assertEquals("Network error [node-4]", error.message)
    }

    @Test
    fun `AuthenticationError is not retriable`() {
        val error = AuthenticationError(NodeId("node-5"), null)
        assertIs<NodeError>(error)
        assertTrue(error !is RetriableError)
        assertEquals("Auth failed [node-5]", error.message)
    }

    @Test
    fun `InvalidRequestError is not retriable`() {
        val error = InvalidRequestError(NodeId("node-6"), null)
        assertTrue(error !is RetriableError)
        assertEquals("Invalid request [node-6]", error.message)
    }

    @Test
    fun `AllNodesUnavailableError contains loadBalancerId`() {
        val error = AllNodesUnavailableError(LoadBalancerId("lb-1"))
        assertEquals("All nodes unavailable in load balancer: lb-1", error.message)
        assertEquals(LoadBalancerId("lb-1"), error.loadBalancerId)
    }

    @Test
    fun `NodeFailurePolicy returns NodeFailureDecision for retriable errors`() {
        val policy = NodeFailurePolicy.Default
        val retriable = RateLimitError(NodeId("node-1"), null)
        assertIs<NodeFailureDecision>(policy.evaluate(retriable))
    }

    @Test
    fun `NodeFailurePolicy returns null for non-retriable errors`() {
        val policy = NodeFailurePolicy.Default
        val nonRetriable = AuthenticationError(NodeId("node-1"), null)
        assertEquals(null, policy.evaluate(nonRetriable))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.CobalErrorTest" -v`
Expected: FAIL - CobalError, RetriableError, RateLimitError, etc. not found

- [ ] **Step 3: Write the implementation**

```kotlin
package me.ahoo.cobal

typealias LoadBalancerId = String
typealias NodeId = String

abstract class CobalError(
    message: String?,
    override val cause: Throwable?
) : RuntimeException(message, cause)

interface RetriableError

open class NodeError(
    val nodeId: NodeId,
    message: String?,
    cause: Throwable?
) : CobalError(message, cause)

class RateLimitError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Rate limited [$nodeId]", cause), RetriableError
class ServerError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Server error [$nodeId]", cause), RetriableError
class TimeoutError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Timeout [$nodeId]", cause), RetriableError
class NetworkError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Network error [$nodeId]", cause), RetriableError
class AuthenticationError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Auth failed [$nodeId]", cause)
class InvalidRequestError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Invalid request [$nodeId]", cause)

class AllNodesUnavailableError(
    val loadBalancerId: LoadBalancerId
) : CobalError("All nodes unavailable in load balancer: $loadBalancerId", null)

data class NodeFailureDecision(val recoverAt: Instant)

fun interface NodeFailurePolicy {
    fun evaluate(error: CobalError): NodeFailureDecision?

    companion object {
        val Default = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.CobalErrorTest" -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/CobalError.kt core/src/test/kotlin/me/ahoo/cobal/CobalErrorTest.kt
git commit -m "feat(core): unify exception hierarchy with RetriableError marker"
```

---

## Task 2: Add ErrorConverter Interface

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/CobalError.kt`
- Test: `core/src/test/kotlin/me/ahoo/cobal/ErrorConverterTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package me.ahoo.cobal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorConverterTest {

    @Test
    fun `ErrorConverter returns null when cannot convert`() {
        val converter = ErrorConverter { nodeId, error ->
            if (error is RateLimitError) error else null
        }
        val cause = RuntimeException("unknown")
        assertNull(converter.convert(NodeId("node-1"), cause))
    }

    @Test
    fun `ErrorConverter converts to CobalError`() {
        val converter = ErrorConverter { nodeId, error ->
            when (error) {
                is RuntimeException -> RateLimitError(nodeId, error)
                else -> null
            }
        }
        val cause = RuntimeException("rate limited")
        val result = converter.convert(NodeId("node-1"), cause)
        assertEquals(result!!.nodeId, NodeId("node-1"))
    }

    @Test
    fun `DefaultErrorConverter returns null for all throwables`() {
        val converter = ErrorConverter.Default
        val cause = RuntimeException("test")
        assertNull(converter.convert(NodeId("node-1"), cause))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.ErrorConverterTest" -v`
Expected: FAIL - ErrorConverter not found

- [ ] **Step 3: Write the implementation**

Add to the end of CobalError.kt:

```kotlin
fun interface ErrorConverter {
    fun convert(nodeId: NodeId, error: Throwable): CobalError?

    companion object {
        val Default = ErrorConverter { _, _ -> null }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.ErrorConverterTest" -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/CobalError.kt core/src/test/kotlin/me/ahoo/cobal/ErrorConverterTest.kt
git commit -m "feat(core): add ErrorConverter interface for external exception conversion"
```

---

## Task 3: Update LoadBalancer.kt Interface

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/LoadBalancer.kt`
- Test: `core/src/test/kotlin/me/ahoo/cobal/LoadBalancerTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package me.ahoo.cobal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LoadBalancerTest {

    @Test
    fun `LoadBalancer has states property and availableStates computed property`() {
        val node = object : Node {
            override val id: NodeId = "node-1"
            override val weight: Int = 1
        }
        val state = DefaultNodeState(node)
        val lb = object : LoadBalancer<Node> {
            override val id: LoadBalancerId = "lb-1"
            override val states: List<NodeState<Node>> = listOf(state)
            override fun choose(): NodeState<Node> = states.first()
        }
        assertEquals(listOf(state), lb.states)
        assertEquals(listOf(state), lb.availableStates)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.LoadBalancerTest" -v`
Expected: FAIL - LoadBalancer.nodes not found, availableStates not found

- [ ] **Step 3: Write the implementation**

```kotlin
package me.ahoo.cobal

interface LoadBalancer<NODE : Node> {
    val id: LoadBalancerId
    val states: List<NodeState<NODE>>
    val availableStates: List<NodeState<NODE>>
        get() = states.filter { it.available }
    fun choose(): NodeState<NODE>
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.LoadBalancerTest" -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/LoadBalancer.kt
git commit -m "refactor(core): rename LoadBalancer.nodes to states, add availableStates computed property"
```

---

## Task 4: Update NodeState.kt - Remove ErrorCategory, Update onFailure

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt`
- Test: `core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package me.ahoo.cobal

import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NodeStateTest {

    @Test
    fun `DefaultNodeState onFailure marks unavailable for retriable error`() {
        val node = object : Node {
            override val id: NodeId = "node-1"
            override val weight: Int = 1
        }
        val events = MutableSharedFlow<NodeEvent>()
        val state = object : NodeState<Node> {
            override val node: Node = node
            override val watch = events
            override val status: NodeStatus = NodeStatus.AVAILABLE
            override val available: Boolean = true
            override fun onFailure(error: CobalError) {}
        }

        val error = RateLimitError(node.id, null)
        state.onFailure(error)
        assertEquals(NodeStatus.UNAVAILABLE, state.status)
        assertFalse(state.available)
    }

    @Test
    fun `DefaultNodeState onFailure does nothing for non-retriable error`() {
        val node = object : Node {
            override val id: NodeId = "node-1"
            override val weight: Int = 1
        }
        val events = MutableSharedFlow<NodeEvent>()
        val state = object : NodeState<Node> {
            override val node: Node = node
            override val watch = events
            override var status: NodeStatus = NodeStatus.AVAILABLE
            override val available: Boolean = true
            override fun onFailure(error: CobalError) {}
        }

        val error = AuthenticationError(node.id, null)
        state.onFailure(error)
        assertEquals(NodeStatus.AVAILABLE, state.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.NodeStateTest" -v`
Expected: FAIL - ErrorCategory, NodeError.onFailure signature

- [ ] **Step 3: Write the implementation**

Remove ErrorCategory enum and old NodeError class from NodeState.kt. NodeFailureDecision is now in CobalError.kt. NodeState.onFailure now accepts CobalError:

```kotlin
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

interface NodeState<NODE : Node> {
    val node: NODE
    val watch: Flow<NodeEvent>
    val status: NodeStatus
    val available: Boolean
        get() = status == NodeStatus.AVAILABLE

    fun onFailure(error: CobalError)
}

class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    private val failurePolicy: NodeFailurePolicy = NodeFailurePolicy.Default,
    private val circuitOpenThreshold: Int = 5,
    private val clock: Clock = Clock.systemUTC()
) : NodeState<NODE> {
    private var recoverAt: Instant? = null
    private var failureCount: Int = 0
    private var circuitOpened: Boolean = false
    private val events = MutableSharedFlow<NodeEvent>()

    override val watch: Flow<NodeEvent> = events.asSharedFlow()

    override val status: NodeStatus
        get() {
            val currentRecoverAt = recoverAt
            return when {
                circuitOpened -> NodeStatus.CIRCUIT_OPEN
                failureCount >= circuitOpenThreshold -> {
                    circuitOpened = true
                    NodeStatus.CIRCUIT_OPEN
                }
                currentRecoverAt == Instant.MAX -> NodeStatus.AVAILABLE
                currentRecoverAt != null && currentRecoverAt.isAfter(clock.instant()) -> NodeStatus.UNAVAILABLE
                else -> {
                    if (currentRecoverAt != null && currentRecoverAt != Instant.MAX) {
                        failureCount = 0
                        recoverAt = null
                        events.tryEmit(NodeEvent.Recovered(node.id))
                    }
                    NodeStatus.AVAILABLE
                }
            }
        }

    override fun onFailure(error: CobalError) {
        failureCount++
        failurePolicy.evaluate(error)?.let { decision ->
            this.recoverAt = decision.recoverAt
            events.tryEmit(NodeEvent.MarkedUnavailable(node.id, recoverAt!!))
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.NodeStateTest" -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/NodeState.kt core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt
git commit -m "refactor(core): remove ErrorCategory, update onFailure to accept CobalError"
```

---

## Task 5: Update RandomLoadBalancer.kt

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt`
- Test: `core/src/test/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancerTest.kt`

- [ ] **Step 1: Read the existing test file**

Run: `cat core/src/test/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancerTest.kt`

- [ ] **Step 2: Verify existing tests fail with new interface**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.RandomLoadBalancerTest" -v`
Expected: FAIL - nodes property not found

- [ ] **Step 3: Write the new implementation**

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.util.concurrent.ThreadLocalRandom

class RandomLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        return available[ThreadLocalRandom.current().nextInt(available.size)]
    }
}
```

- [ ] **Step 4: Update the test to use states instead of nodes**

The existing test will need updates. Read the test file and update it to pass NodeState instead of Node:

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import me.ahoo.cobal.NodeStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RandomLoadBalancerTest {

    private fun node(id: String, weight: Int = 1): Node = object : Node {
        override val id: me.ahoo.cobal.NodeId = id
        override val weight: Int = weight
    }

    @Test
    fun `choose returns random available state`() {
        val node1 = node("node-1")
        val node2 = node("node-2")
        val states = listOf(
            DefaultNodeState(node1),
            DefaultNodeState(node2)
        )
        val lb = RandomLoadBalancer("lb-1", states)
        val chosen = lb.choose()
        assertIs<NodeState<Node>>(chosen)
        assertTrue(chosen.available)
    }

    @Test
    fun `availableStates filters unavailable states`() {
        val node1 = node("node-1")
        val node2 = node("node-2")
        val state1 = DefaultNodeState(node1)
        val state2 = object : NodeState<Node> {
            override val node: Node = node2
            override val watch = me.ahoo.cobal.flow.MutableSharedFlow()
            override val status: NodeStatus = NodeStatus.UNAVAILABLE
            override val available: Boolean = false
            override fun onFailure(error: me.ahoo.cobal.CobalError) {}
        }
        val states = listOf(state1, state2)
        val lb = RandomLoadBalancer("lb-1", states)
        assertEquals(1, lb.availableStates.size)
        assertEquals(node1.id, lb.availableStates[0].node.id)
    }

    @Test
    fun `choose throws AllNodesUnavailableError when no available states`() {
        val node1 = node("node-1")
        val state1 = object : NodeState<Node> {
            override val node: Node = node1
            override val watch = me.ahoo.cobal.flow.MutableSharedFlow()
            override val status: NodeStatus = NodeStatus.UNAVAILABLE
            override val available: Boolean = false
            override fun onFailure(error: me.ahoo.cobal.CobalError) {}
        }
        val lb = RandomLoadBalancer("lb-1", listOf(state1))
        assertThrows<AllNodesUnavailableError> { lb.choose() }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.RandomLoadBalancerTest" -v`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt core/src/test/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancerTest.kt
git commit -m "refactor(core): update RandomLoadBalancer to use states instead of nodes"
```

---

## Task 6: Update RoundRobinLoadBalancer.kt

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt`
- Test: `core/src/test/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancerTest.kt`

- [ ] **Step 1: Read existing RoundRobinLoadBalancer.kt and test file**

- [ ] **Step 2: Update to use states**

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.util.concurrent.atomic.AtomicInteger

class RoundRobinLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    private val index = AtomicInteger(0)

    override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        val currentIndex = index.getAndIncrement()
        return available[currentIndex % available.size]
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.RoundRobinLoadBalancerTest" -v`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt core/src/test/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancerTest.kt
git commit -m "refactor(core): update RoundRobinLoadBalancer to use states"
```

---

## Task 7: Update WeightedRoundRobinLoadBalancer.kt

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt`
- Test: `core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancerTest.kt`

- [ ] **Step 1: Read existing WeightedRoundRobinLoadBalancer.kt and test file**

- [ ] **Step 2: Update to use states**

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AllNodesUnavailableError
import me.ahoo.cobal.LoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.util.concurrent.atomic.AtomicInteger

class WeightedRoundRobinLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    private val currentWeight = AtomicInteger(0)

    override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        val selected = selectByWeight(available)
        return selected
    }

    private fun selectByWeight(states: List<NodeState<NODE>>): NodeState<NODE> {
        var maxWeight = 0
        for (state in states) {
            if (state.node.weight > maxWeight) {
                maxWeight = state.node.weight
            }
        }
        var current = currentWeight.get()
        while (current < maxWeight) {
            if (currentWeight.compareAndSet(current, current + 1)) {
                current++
            }
        }
        val selectedIndex = (current - 1) % states.size
        return states[selectedIndex]
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRoundRobinLoadBalancerTest" -v`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancerTest.kt
git commit -m "refactor(core): update WeightedRoundRobinLoadBalancer to use states"
```

---

## Task 8: Update LangChain4j LoadBalancedChatModel.kt

**Files:**
- Modify: `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedChatModel.kt`

- [ ] **Step 1: Read the file**

- [ ] **Step 2: Find and replace AllNodesUnavailableException → AllNodesUnavailableError**

- [ ] **Step 3: Commit**

```bash
git add langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedChatModel.kt
git commit -m "fix(langchain4j): rename AllNodesUnavailableException to AllNodesUnavailableError"
```

---

## Task 9: Update Remaining LangChain4j Files

**Files:**
- Modify: `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedStreamingChatModel.kt`
- Modify: `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedEmbeddingModel.kt`
- Modify: `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedImageModel.kt`
- Modify: `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedAudioTranscriptionModel.kt`

- [ ] **Step 1: Apply AllNodesUnavailableException → AllNodesUnavailableError to each file**

- [ ] **Step 2: Commit**

```bash
git add langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedStreamingChatModel.kt langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedEmbeddingModel.kt langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedImageModel.kt langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/LoadBalancedAudioTranscriptionModel.kt
git commit -m "fix(langchain4j): rename AllNodesUnavailableException to AllNodesUnavailableError in remaining models"
```

---

## Task 10: Update Spring AI LoadBalancedChatModel.kt

**Files:**
- Modify: `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedChatModel.kt`

- [ ] **Step 1: Read the file**

- [ ] **Step 2: Find and replace AllNodesUnavailableException → AllNodesUnavailableError**

- [ ] **Step 3: Commit**

```bash
git add spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedChatModel.kt
git commit -m "fix(spring-ai): rename AllNodesUnavailableException to AllNodesUnavailableError"
```

---

## Task 11: Update Remaining Spring AI Files

**Files:**
- Modify: `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedEmbeddingModel.kt`
- Modify: `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedImageModel.kt`
- Modify: `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedAudioTranscriptionModel.kt`

- [ ] **Step 1: Apply AllNodesUnavailableException → AllNodesUnavailableError to each file**

- [ ] **Step 2: Commit**

```bash
git add spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedEmbeddingModel.kt spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedImageModel.kt spring-ai/src/main/kotlin/me/ahoo/cobal/springai/LoadBalancedAudioTranscriptionModel.kt
git commit -m "fix(spring-ai): rename AllNodesUnavailableException to AllNodesUnavailableError in remaining models"
```

---

## Task 12: Full Build Verification

- [ ] **Step 1: Run all tests**

Run: `./gradlew :core:test :langchain4j:test :spring-ai:test`
Expected: All tests pass

- [ ] **Step 2: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run detekt**

Run: `./gradlew detekt`
Expected: All clean

---

## Spec Coverage Check

| Spec Requirement | Task |
|-----------------|------|
| LoadBalancer.nodes → states | Task 3 |
| availableStates as val computed property | Task 3 |
| CobalError abstract base | Task 1 |
| RetriableError marker interface | Task 1 |
| NodeError with nodeId | Task 1 |
| RateLimitError, ServerError, TimeoutError, NetworkError (retriable) | Task 1 |
| AuthenticationError, InvalidRequestError (non-retriable) | Task 1 |
| AllNodesUnavailableError | Task 1 (already existed, moved) |
| NodeFailurePolicy.evaluate returns RetriableError? | Task 1 |
| ErrorConverter interface | Task 2 |
| NodeState.onFailure accepts CobalError | Task 4 |
| ErrorCategory removed | Task 4 |
| RandomLoadBalancer updated | Task 5 |
| RoundRobinLoadBalancer updated | Task 6 |
| WeightedRoundRobinLoadBalancer updated | Task 7 |
| LangChain4j models updated | Tasks 8-9 |
| Spring AI models updated | Tasks 10-11 |
