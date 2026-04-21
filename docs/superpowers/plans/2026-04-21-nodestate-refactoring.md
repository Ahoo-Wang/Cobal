# NodeState Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `DefaultNodeState` to use `AtomicReference<NodeStat>` for lock-free thread safety, replace passive time-checking with coroutine-scheduled recovery, and simplify the `status` property to a direct read.

**Architecture:** Introduce a `NodeStat` data class holding all mutable state in a single immutable object. State transitions use `AtomicReference.updateAndGet` (CAS loop) — no `synchronized`. Recovery is scheduled via `CoroutineScope.launch { delay(...) }` instead of checking `Instant` on every `status` read. The `scope` parameter has a default value so most callers need no changes.

**Tech Stack:** Kotlin 2.3.20, kotlinx-coroutines-core, java.util.concurrent.atomic.AtomicReference, JUnit 5, kotlinx-coroutines-test

---

### Task 1: Add NodeStat data class and refactor DefaultNodeState core

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt`

- [ ] **Step 1: Rewrite NodeState.kt**

Replace the entire `DefaultNodeState` class body. Keep `NodeStatus`, `NodeEvent`, and the `NodeState<NODE>` interface unchanged. Add the `NodeStat` data class inside `DefaultNodeState` as a private nested class, or as a top-level data class in the same file.

Full replacement for the class portion of `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt` (everything after the `NodeState<NODE>` interface):

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

data class NodeStat(
    val failureCount: Int = 0,
    val circuitOpened: Boolean = false,
    val status: NodeStatus = NodeStatus.AVAILABLE,
)

class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val failurePolicy: NodeFailurePolicy = NodeFailurePolicy.Default,
    private val circuitOpenThreshold: Int = 5,
) : NodeState<NODE> {
    private val stat = AtomicReference(NodeStat())
    private val events = MutableSharedFlow<NodeEvent>()
    private var recoveryJob: Job? = null

    override val watch: Flow<NodeEvent> = events.asSharedFlow()

    override val status: NodeStatus
        get() = stat.get().status

    override fun onFailure(error: CobalError) {
        val decision = failurePolicy.evaluate(error)
        var becameCircuitOpen = false
        var recoveredAt: Instant? = null

        stat.updateAndGet { current ->
            val newCount = current.failureCount + 1
            val newOpened = newCount >= circuitOpenThreshold
            val newStatus = when {
                current.status == NodeStatus.CIRCUIT_HALF_OPEN -> NodeStatus.CIRCUIT_OPEN
                newOpened -> NodeStatus.CIRCUIT_OPEN
                decision != null -> NodeStatus.UNAVAILABLE
                else -> current.status
            }
            becameCircuitOpen = newOpened && !current.circuitOpened
            recoveredAt = decision?.recoverAt
            NodeStat(
                failureCount = newCount,
                circuitOpened = newOpened || current.circuitOpened,
                status = newStatus,
            )
        }

        if (recoveredAt != null) {
            events.tryEmit(NodeEvent.MarkedUnavailable(node.id, recoveredAt!!))
            scheduleRecovery(recoveredAt!!)
        }
        if (becameCircuitOpen) {
            events.tryEmit(NodeEvent.CircuitOpened(node.id))
        }
    }

    override fun onSuccess() {
        stat.updateAndGet { current ->
            when (current.status) {
                NodeStatus.CIRCUIT_HALF_OPEN,
                NodeStatus.UNAVAILABLE -> NodeStat()
                else -> current.copy(failureCount = 0)
            }
        }
        recoveryJob?.cancel()
        events.tryEmit(NodeEvent.Recovered(node.id))
    }

    private fun scheduleRecovery(recoverAt: Instant) {
        recoveryJob?.cancel()
        val duration = Duration.between(Instant.now(), recoverAt)
        if (duration.isNegative || duration.isZero) {
            recover()
            return
        }
        recoveryJob = scope.launch {
            delay(duration)
            recover()
        }
    }

    private fun recover() {
        stat.updateAndGet { current ->
            when (current.status) {
                NodeStatus.UNAVAILABLE -> current.copy(status = NodeStatus.AVAILABLE)
                NodeStatus.CIRCUIT_OPEN -> current.copy(status = NodeStatus.CIRCUIT_HALF_OPEN)
                else -> current
            }
        }
        events.tryEmit(NodeEvent.Recovered(node.id))
    }
}
```

Remove the old imports (`java.time.Clock`) and add the new ones listed above. The `NodeStatus` enum, `NodeEvent` sealed interface, and `NodeState<NODE>` interface stay exactly as-is.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/NodeState.kt
git commit -m "refactor(core): replace synchronized with AtomicReference and scheduled recovery in NodeState"
```

---

### Task 2: Rewrite NodeStateTest

**Files:**
- Modify: `core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt`

- [ ] **Step 1: Rewrite the test file**

Remove `MutableClock`, `SimpleNodeForState`. Use `kotlinx.coroutines.test.TestScope` and `advanceTimeBy` for time-based tests. Use coroutine-based concurrency test.

Full replacement for `core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt`:

```kotlin
package me.ahoo.cobal

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Instant

class NodeStateTest {

    @Test
    fun `DefaultNodeState onFailure marks unavailable for retriable error`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)

        val error = RateLimitError(node.id, null)
        state.onFailure(error)
        state.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        state.available.assert().isFalse()
    }

    @Test
    fun `onSuccess should reset failure count`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitOpenThreshold = 3)

        state.onFailure(RateLimitError(node.id, null))
        state.onFailure(RateLimitError(node.id, null))
        state.onSuccess()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
    }

    @Test
    fun `circuit breaker should open after threshold failures`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitOpenThreshold = 3)

        repeat(3) {
            state.onFailure(RateLimitError(node.id, null))
        }

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `circuit breaker should transition to HALF_OPEN after recovery time`() = runTest {
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, scope = this, failurePolicy = testPolicy, circuitOpenThreshold = 2)

        state.onFailure(RateLimitError(node.id, null))
        state.onFailure(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        advanceTimeBy(60_000)

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)
        state.available.assert().isTrue()
    }

    @Test
    fun `onSuccess in HALF_OPEN should transition to AVAILABLE`() = runTest {
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, scope = this, failurePolicy = testPolicy, circuitOpenThreshold = 2)

        state.onFailure(RateLimitError(node.id, null))
        state.onFailure(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        advanceTimeBy(60_000)
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.onSuccess()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        state.available.assert().isTrue()
    }

    @Test
    fun `onFailure in HALF_OPEN should re-open circuit`() = runTest {
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, scope = this, failurePolicy = testPolicy, circuitOpenThreshold = 2)

        state.onFailure(RateLimitError(node.id, null))
        state.onFailure(RateLimitError(node.id, null))

        advanceTimeBy(60_000)
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.onFailure(ServerError(node.id, null))

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `concurrent onFailure calls should be thread-safe`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitOpenThreshold = 100)
        val threadCount = 50
        val threads = mutableListOf<Thread>()
        val errorCount = java.util.concurrent.atomic.AtomicInteger(0)

        repeat(threadCount) {
            val t = Thread {
                try {
                    state.onFailure(RateLimitError(node.id, null))
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
            threads.add(t)
            t.start()
        }
        threads.forEach { it.join() }

        errorCount.get().assert().isEqualTo(0)
        state.status.assert().isNotNull()
    }
}
```

Key changes from old tests:
- `MutableClock` and `SimpleNodeForState` removed — use `DefaultNode` (already exists)
- Time-based tests use `runTest { ... advanceTimeBy(...) }` with `scope = this`
- `onFailure in HALF_OPEN` test now drives through HALF_OPEN via `advanceTimeBy` (old version didn't properly enter HALF_OPEN)
- Concurrency test uses plain `Thread` + `join` instead of `CountDownLatch` (simpler, same coverage)
- `kotlinx.coroutines.test` dependency is already available via `kotlinx-coroutines-core`

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.NodeStateTest"`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt
git commit -m "test(core): rewrite NodeStateTest with TestScope and coroutine time control"
```

---

### Task 3: Update integration module NodeState extension functions

**Files:**
- Modify: `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/NodeState.kt`
- Modify: `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/NodeState.kt`

- [ ] **Step 1: Update langchain4j NodeState.kt**

Replace `clock: Clock` parameter with `scope: CoroutineScope` (with default):

```kotlin
package me.ahoo.cobal.langchain4j

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState

fun <NODE : Node> NODE.toNodeState(
    circuitOpenThreshold: Int = 5,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): DefaultNodeState<NODE> = DefaultNodeState(this, scope, LangChain4jFailurePolicy, circuitOpenThreshold)

fun <NODE : Node> List<NODE>.toNodeStates(
    circuitOpenThreshold: Int = 5,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): List<NodeState<NODE>> = map { it.toNodeState(circuitOpenThreshold, scope) }
```

- [ ] **Step 2: Update spring-ai NodeState.kt**

Same pattern:

```kotlin
package me.ahoo.cobal.springai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState

fun <NODE : Node> NODE.toNodeState(
    circuitOpenThreshold: Int = 5,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): DefaultNodeState<NODE> = DefaultNodeState(this, scope, SpringAiFailurePolicy, circuitOpenThreshold)

fun <NODE : Node> List<NODE>.toNodeStates(
    circuitOpenThreshold: Int = 5,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): List<NodeState<NODE>> = map { it.toNodeState(circuitOpenThreshold, scope) }
```

- [ ] **Step 3: Verify both modules compile**

Run: `./gradlew :langchain4j:compileKotlin :spring-ai:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/NodeState.kt spring-ai/src/main/kotlin/me/ahoo/cobal/springai/NodeState.kt
git commit -m "refactor: replace clock with CoroutineScope in integration module NodeState extensions"
```

---

### Task 4: Run full test suite

**Files:** None (verification only)

- [ ] **Step 1: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all tests across core, langchain4j, spring-ai pass

- [ ] **Step 2: Run Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Fix any failures if needed**

If any test fails due to the `DefaultNodeState` constructor change (unlikely since `scope` has a default), add `scope = TestScope()` to the failing test's `DefaultNodeState` construction.
