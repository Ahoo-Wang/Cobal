# CircuitBreaker Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract circuit breaker logic from `DefaultNodeState` into a standalone `CircuitBreaker` interface with `DefaultCircuitBreaker` implementation.

**Architecture:** New `CircuitBreaker` interface holds its own state (failure count, opened timestamp) via `AtomicReference`. `DefaultNodeState` delegates all CIRCUIT_OPEN/CIRCUIT_HALF_OPEN transitions to the injected `CircuitBreaker`. NodeState retains only policy-driven UNAVAILABLE logic.

**Tech Stack:** Kotlin 2.3.20, JUnit 5, kotlinx-coroutines-test, MockK, fluent-assert

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `core/src/main/kotlin/me/ahoo/cobal/CircuitBreaker.kt` | Create | CircuitBreakerState, CircuitBreakerTransition, CircuitBreaker interface, DefaultCircuitBreaker, CircuitBreakerStat |
| `core/src/test/kotlin/me/ahoo/cobal/CircuitBreakerTest.kt` | Create | Tests for DefaultCircuitBreaker state machine |
| `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt` | Modify | Add `circuitBreaker` to NodeState interface, simplify NodeStat, refactor DefaultNodeState to delegate |
| `core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt` | Modify | Update constructor calls, add CB integration tests |
| `core/src/test/kotlin/me/ahoo/cobal/AbstractLoadBalancedModelTest.kt` | Modify | Update one `circuitOpenThreshold` constructor call |
| `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/NodeState.kt` | Modify | Update factory to use `DefaultCircuitBreaker` |
| `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/NodeState.kt` | Modify | Update factory to use `DefaultCircuitBreaker` |

All other test files use `DefaultNodeState(node)` (single-arg constructor) and will compile without changes since `circuitBreaker` has a default value.

---

### Task 1: Create CircuitBreaker types and DefaultCircuitBreaker

**Files:**
- Create: `core/src/main/kotlin/me/ahoo/cobal/CircuitBreaker.kt`
- Create: `core/src/test/kotlin/me/ahoo/cobal/CircuitBreakerTest.kt`

- [ ] **Step 1: Write CircuitBreakerTest with failing tests**

Create `core/src/test/kotlin/me/ahoo/cobal/CircuitBreakerTest.kt`:

```kotlin
package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class CircuitBreakerTest {

    @Test
    fun `DefaultCircuitBreaker should start CLOSED`() {
        val cb = DefaultCircuitBreaker()
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
        cb.recoverAt.assert().isNull()
    }

    @Test
    fun `onError should return null when below threshold`() {
        val cb = DefaultCircuitBreaker(threshold = 3)
        cb.onError().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
    }

    @Test
    fun `onError should return Opened at threshold`() {
        val cb = DefaultCircuitBreaker(threshold = 3)

        cb.onError().assert().isNull()
        cb.onError().assert().isNull()
        cb.onError().assert().isInstanceOf(CircuitBreakerTransition.Opened::class.java)

        cb.state.assert().isEqualTo(CircuitBreakerState.OPEN)
        cb.recoverAt.assert().isNotNull()
    }

    @Test
    fun `onError should return ReHalfOpened when HALF_OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 2)
        cb.onError()
        cb.onError()

        cb.tryRecover()
        cb.state.assert().isEqualTo(CircuitBreakerState.HALF_OPEN)

        cb.onError().assert().isInstanceOf(CircuitBreakerTransition.ReHalfOpened::class.java)
        cb.state.assert().isEqualTo(CircuitBreakerState.OPEN)
    }

    @Test
    fun `onError should return null when OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 1)
        cb.onError()

        cb.onError().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.OPEN)
    }

    @Test
    fun `onSuccess should return null and reset count when CLOSED`() {
        val cb = DefaultCircuitBreaker(threshold = 3)
        cb.onError()
        cb.onError()

        cb.onSuccess().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)

        // Count was reset — takes another 3 errors to open
        cb.onError().assert().isNull()
        cb.onError().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
    }

    @Test
    fun `onSuccess should return Closed when HALF_OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 1)
        cb.onError()
        cb.tryRecover()
        cb.state.assert().isEqualTo(CircuitBreakerState.HALF_OPEN)

        cb.onSuccess().assert().isInstanceOf(CircuitBreakerTransition.Closed::class.java)
        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
        cb.recoverAt.assert().isNull()
    }

    @Test
    fun `tryRecover should return HalfOpened when OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 1, recoveryDuration = Duration.ofSeconds(30))
        cb.onError()

        val transition = cb.tryRecover()
        transition.assert().isInstanceOf(CircuitBreakerTransition.HalfOpened::class.java)
        cb.state.assert().isEqualTo(CircuitBreakerState.HALF_OPEN)
    }

    @Test
    fun `tryRecover should return null when CLOSED`() {
        val cb = DefaultCircuitBreaker()
        cb.tryRecover().assert().isNull()
    }

    @Test
    fun `tryRecover should return null when HALF_OPEN`() {
        val cb = DefaultCircuitBreaker(threshold = 1)
        cb.onError()
        cb.tryRecover()

        cb.tryRecover().assert().isNull()
        cb.state.assert().isEqualTo(CircuitBreakerState.HALF_OPEN)
    }

    @Test
    fun `recoverAt should be openedAt plus recoveryDuration`() {
        val before = Instant.now()
        val cb = DefaultCircuitBreaker(threshold = 1, recoveryDuration = Duration.ofSeconds(30))
        cb.onError()
        val after = Instant.now()

        val recoverAt = cb.recoverAt!!
        val expectedMin = before.plusSeconds(30)
        val expectedMax = after.plusSeconds(30)
        recoverAt.assert().isGreaterThanOrEqualTo(expectedMin)
        recoverAt.assert().isLessThanOrEqualTo(expectedMax)
    }

    @Test
    fun `recoverAt should be null when CLOSED`() {
        val cb = DefaultCircuitBreaker(threshold = 1, recoveryDuration = Duration.ofSeconds(30))
        cb.recoverAt.assert().isNull()
    }

    @Test
    fun `onSuccess resets count after HALF_OPEN to Closed cycle`() {
        val cb = DefaultCircuitBreaker(threshold = 2)
        cb.onError()
        cb.onError()
        cb.tryRecover()
        cb.onSuccess()

        cb.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
        // Failure count is reset
        cb.onError().assert().isNull()
        cb.onError().assert().isInstanceOf(CircuitBreakerTransition.Opened::class.java)
    }

    @Test
    fun `ReHalfOpened should update openedAt`() {
        val cb = DefaultCircuitBreaker(threshold = 1, recoveryDuration = Duration.ofSeconds(10))
        cb.onError()
        val firstRecoverAt = cb.recoverAt!!

        cb.tryRecover()
        Thread.sleep(1)
        cb.onError()

        val secondRecoverAt = cb.recoverAt!!
        secondRecoverAt.assert().isGreaterThan(firstRecoverAt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.CircuitBreakerTest" 2>&1 | tail -20`
Expected: COMPILATION ERROR — `CircuitBreakerState`, `CircuitBreakerTransition`, `CircuitBreaker`, `DefaultCircuitBreaker` not found.

- [ ] **Step 3: Create CircuitBreaker.kt with all types and DefaultCircuitBreaker**

Create `core/src/main/kotlin/me/ahoo/cobal/CircuitBreaker.kt`:

```kotlin
package me.ahoo.cobal

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

sealed interface CircuitBreakerTransition {
    data object Opened : CircuitBreakerTransition
    data object ReHalfOpened : CircuitBreakerTransition
    data object HalfOpened : CircuitBreakerTransition
    data object Closed : CircuitBreakerTransition
}

interface CircuitBreaker {
    val state: CircuitBreakerState
    val recoverAt: Instant?

    fun onError(): CircuitBreakerTransition?
    fun onSuccess(): CircuitBreakerTransition?
    fun tryRecover(): CircuitBreakerTransition?
}

internal data class CircuitBreakerStat(
    val failureCount: Int = 0,
    val state: CircuitBreakerState = CircuitBreakerState.CLOSED,
    val openedAt: Instant? = null,
) {
    companion object {
        val Default = CircuitBreakerStat()
    }
}

class DefaultCircuitBreaker(
    private val threshold: Int = 5,
    private val recoveryDuration: Duration = Duration.ofSeconds(60),
) : CircuitBreaker {
    private val stat = AtomicReference(CircuitBreakerStat.Default)

    override val state: CircuitBreakerState
        get() = stat.get().state

    override val recoverAt: Instant?
        get() = stat.get().openedAt?.plus(recoveryDuration)

    override fun onError(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            when (current.state) {
                CircuitBreakerState.HALF_OPEN -> {
                    transition = CircuitBreakerTransition.ReHalfOpened
                    current.copy(state = CircuitBreakerState.OPEN, openedAt = Instant.now(), failureCount = current.failureCount + 1)
                }

                CircuitBreakerState.CLOSED -> {
                    val newCount = current.failureCount + 1
                    if (newCount >= threshold) {
                        transition = CircuitBreakerTransition.Opened
                        current.copy(state = CircuitBreakerState.OPEN, openedAt = Instant.now(), failureCount = newCount)
                    } else {
                        current.copy(failureCount = newCount)
                    }
                }

                CircuitBreakerState.OPEN -> current
            }
        }
        return transition
    }

    override fun onSuccess(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            when (current.state) {
                CircuitBreakerState.HALF_OPEN -> {
                    transition = CircuitBreakerTransition.Closed
                    CircuitBreakerStat.Default
                }

                CircuitBreakerState.CLOSED -> current.copy(failureCount = 0)

                CircuitBreakerState.OPEN -> current
            }
        }
        return transition
    }

    override fun tryRecover(): CircuitBreakerTransition? {
        var transition: CircuitBreakerTransition? = null
        stat.updateAndGet { current ->
            if (current.state == CircuitBreakerState.OPEN) {
                transition = CircuitBreakerTransition.HalfOpened
                current.copy(state = CircuitBreakerState.HALF_OPEN)
            } else {
                current
            }
        }
        return transition
    }
}
```

- [ ] **Step 4: Run CircuitBreakerTest to verify all pass**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.CircuitBreakerTest" 2>&1 | tail -20`
Expected: All 14 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/CircuitBreaker.kt core/src/test/kotlin/me/ahoo/cobal/CircuitBreakerTest.kt
git commit -m "feat(core): add CircuitBreaker interface and DefaultCircuitBreaker"
```

---

### Task 2: Refactor NodeState to delegate to CircuitBreaker

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt`

- [ ] **Step 1: Refactor NodeState.kt**

Replace the entire content of `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt` with:

```kotlin
package me.ahoo.cobal

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

enum class NodeStatus {
    AVAILABLE,
    UNAVAILABLE,
    CIRCUIT_OPEN,
    CIRCUIT_HALF_OPEN
}

sealed interface NodeEvent {
    val nodeId: NodeId

    data class MarkedUnavailable(override val nodeId: NodeId, val recoverAt: Instant) : NodeEvent
    data class Recovered(override val nodeId: NodeId) : NodeEvent
    data class CircuitOpened(override val nodeId: NodeId) : NodeEvent
    data class CircuitHalfOpen(override val nodeId: NodeId) : NodeEvent
}

interface NodeState<NODE : Node> {
    val node: NODE
    val watch: Flow<NodeEvent>
    val status: NodeStatus
    val available: Boolean
        get() = status == NodeStatus.AVAILABLE || status == NodeStatus.CIRCUIT_HALF_OPEN
    val circuitBreaker: CircuitBreaker

    fun onError(error: CobalError)
    fun onSuccess()
}

internal data class NodeStat(
    val nodeStatus: NodeStatus = NodeStatus.AVAILABLE,
) {
    companion object {
        val Default = NodeStat()
    }
}

class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val failurePolicy: NodeFailurePolicy = NodeFailurePolicy.Default,
    override val circuitBreaker: CircuitBreaker = DefaultCircuitBreaker(),
) : NodeState<NODE> {
    private val stat = AtomicReference(NodeStat.Default)
    private val events = MutableSharedFlow<NodeEvent>()

    @Volatile
    private var recoveryJob: Job? = null

    override val watch: Flow<NodeEvent> = events.asSharedFlow()

    override val status: NodeStatus
        get() = when (circuitBreaker.state) {
            CircuitBreakerState.OPEN -> NodeStatus.CIRCUIT_OPEN
            CircuitBreakerState.HALF_OPEN -> NodeStatus.CIRCUIT_HALF_OPEN
            CircuitBreakerState.CLOSED -> stat.get().nodeStatus
        }

    override fun onError(error: CobalError) {
        val decision = failurePolicy.evaluate(error)
        val cbTransition = circuitBreaker.onError()

        if (decision != null) {
            stat.updateAndGet { it.copy(nodeStatus = NodeStatus.UNAVAILABLE) }
            events.tryEmit(NodeEvent.MarkedUnavailable(node.id, decision.recoverAt))
            scheduleRecovery(decision.recoverAt)
        }

        when (cbTransition) {
            is CircuitBreakerTransition.Opened,
            is CircuitBreakerTransition.ReHalfOpened,
            -> {
                events.tryEmit(NodeEvent.CircuitOpened(node.id))
                circuitBreaker.recoverAt?.let { scheduleRecovery(it) }
            }
            else -> Unit
        }
    }

    override fun onSuccess() {
        val cbTransition = circuitBreaker.onSuccess()
        val wasUnavailable = stat.get().nodeStatus == NodeStatus.UNAVAILABLE

        if (cbTransition != null || wasUnavailable) {
            recoveryJob?.cancel()
            stat.updateAndGet { NodeStat.Default }
            events.tryEmit(NodeEvent.Recovered(node.id))
        }
    }

    private fun scheduleRecovery(recoverAt: Instant) {
        recoveryJob?.cancel()
        val duration = Duration.between(Instant.now(), recoverAt)
        if (duration.isNegative || duration.isZero) {
            recover()
            return
        }
        recoveryJob = scope.launch {
            delay(duration.toMillis())
            recover()
        }
    }

    private fun recover() {
        val cbTransition = circuitBreaker.tryRecover()
        val wasUnavailable = stat.get().nodeStatus == NodeStatus.UNAVAILABLE

        if (wasUnavailable) {
            stat.updateAndGet { NodeStat.Default }
            events.tryEmit(NodeEvent.Recovered(node.id))
        }

        if (cbTransition is CircuitBreakerTransition.HalfOpened) {
            events.tryEmit(NodeEvent.CircuitHalfOpen(node.id))
        }
    }
}
```

Key changes from original:
- `NodeState` interface gains `circuitBreaker: CircuitBreaker` property
- `NodeStat` simplified — only `nodeStatus` (AVAILABLE or UNAVAILABLE), no failureCount/circuitOpened
- `DefaultNodeState` constructor: `circuitOpenThreshold: Int` replaced by `circuitBreaker: CircuitBreaker` with `DefaultCircuitBreaker()` default
- `onError()` delegates to `circuitBreaker.onError()`, handles both policy and CB transitions
- `onSuccess()` delegates to `circuitBreaker.onSuccess()`, resets on any recovery
- `recover()` calls `circuitBreaker.tryRecover()` instead of directly mutating NodeStat
- `status` is now computed from `circuitBreaker.state` + `stat.nodeStatus`

- [ ] **Step 2: Verify core compiles**

Run: `./gradlew :core:compileKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL (some tests may fail due to constructor changes — compile only for now)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/NodeState.kt
git commit -m "refactor(core): extract circuit breaker logic from NodeState into CircuitBreaker"
```

---

### Task 3: Update NodeStateTest

**Files:**
- Modify: `core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt`

- [ ] **Step 1: Rewrite NodeStateTest**

Replace the entire content of `core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt` with:

```kotlin
package me.ahoo.cobal

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class NodeStateTest {

    @Test
    fun `DefaultNodeState onError marks unavailable for retriable error`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node)

        val error = RateLimitError(node.id, null)
        state.onError(error)
        state.status.assert().isEqualTo(NodeStatus.UNAVAILABLE)
        state.available.assert().isFalse()
    }

    @Test
    fun `onSuccess should reset failure count`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 3))

        state.onError(RateLimitError(node.id, null))
        state.onError(RateLimitError(node.id, null))
        state.onSuccess()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
    }

    @Test
    fun `circuit breaker should open after threshold failures`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 3))

        repeat(3) {
            state.onError(RateLimitError(node.id, null))
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
        val state = DefaultNodeState(
            node,
            scope = this,
            failurePolicy = testPolicy,
            circuitBreaker = DefaultCircuitBreaker(threshold = 2, recoveryDuration = Duration.ofSeconds(30)),
        )

        state.onError(RateLimitError(node.id, null))
        state.onError(RateLimitError(node.id, null))
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
        val state = DefaultNodeState(
            node,
            scope = this,
            failurePolicy = testPolicy,
            circuitBreaker = DefaultCircuitBreaker(threshold = 2, recoveryDuration = Duration.ofSeconds(30)),
        )

        state.onError(RateLimitError(node.id, null))
        state.onError(RateLimitError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)

        advanceTimeBy(60_000)
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.onSuccess()

        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)
        state.available.assert().isTrue()
    }

    @Test
    fun `onError in HALF_OPEN should re-open circuit`() = runTest {
        val testPolicy = NodeFailurePolicy { error ->
            when (error) {
                is RetriableError -> NodeFailureDecision(Instant.now().plusSeconds(30))
                else -> null
            }
        }
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(
            node,
            scope = this,
            failurePolicy = testPolicy,
            circuitBreaker = DefaultCircuitBreaker(threshold = 2, recoveryDuration = Duration.ofSeconds(30)),
        )

        state.onError(RateLimitError(node.id, null))
        state.onError(RateLimitError(node.id, null))

        advanceTimeBy(60_000)
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_HALF_OPEN)

        state.onError(ServerError(node.id, null))

        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
        state.available.assert().isFalse()
    }

    @Test
    fun `concurrent onError calls should be thread-safe`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 100))
        val threadCount = 50
        val threads = mutableListOf<Thread>()
        val errorCount = java.util.concurrent.atomic.AtomicInteger(0)

        repeat(threadCount) {
            val t = Thread {
                try {
                    state.onError(RateLimitError(node.id, null))
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

    @Test
    fun `NodeState should expose circuitBreaker property`() {
        val node = DefaultNode("node-1")
        val cb = DefaultCircuitBreaker(threshold = 3)
        val state = DefaultNodeState(node, circuitBreaker = cb)

        state.circuitBreaker.assert().isSameAs(cb)
        state.circuitBreaker.state.assert().isEqualTo(CircuitBreakerState.CLOSED)
    }

    @Test
    fun `non-policy errors should open circuit without UNAVAILABLE`() {
        val node = DefaultNode("node-1")
        val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 2))

        state.onError(ServerError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.AVAILABLE)

        state.onError(ServerError(node.id, null))
        state.status.assert().isEqualTo(NodeStatus.CIRCUIT_OPEN)
    }
}
```

Changes from original:
- All `circuitOpenThreshold = N` constructor calls changed to `circuitBreaker = DefaultCircuitBreaker(threshold = N)`
- All `circuitOpenThreshold = 2` with `scope = this` tests also now pass explicit `recoveryDuration = Duration.ofSeconds(30)` to the CB
- Added `NodeState should expose circuitBreaker property` test
- Added `non-policy errors should open circuit without UNAVAILABLE` test (verifies the new bug fix)

- [ ] **Step 2: Run NodeStateTest**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.NodeStateTest" 2>&1 | tail -20`
Expected: All 9 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt
git commit -m "test(core): update NodeStateTest for CircuitBreaker delegation"
```

---

### Task 4: Update AbstractLoadBalancedModelTest

**Files:**
- Modify: `core/src/test/kotlin/me/ahoo/cobal/AbstractLoadBalancedModelTest.kt`

- [ ] **Step 1: Update the single constructor call**

In `core/src/test/kotlin/me/ahoo/cobal/AbstractLoadBalancedModelTest.kt`, line 112, change:

```kotlin
val state = DefaultNodeState(node, circuitOpenThreshold = 2)
```

to:

```kotlin
val state = DefaultNodeState(node, circuitBreaker = DefaultCircuitBreaker(threshold = 2))
```

Also add the import at the top of the file (after existing imports):

```kotlin
import java.time.Duration
```

- [ ] **Step 2: Run AbstractLoadBalancedModelTest**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.AbstractLoadBalancedModelTest" 2>&1 | tail -10`
Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/kotlin/me/ahoo/cobal/AbstractLoadBalancedModelTest.kt
git commit -m "test(core): update AbstractLoadBalancedModelTest for CircuitBreaker constructor"
```

---

### Task 5: Update integration module factory functions

**Files:**
- Modify: `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/NodeState.kt`
- Modify: `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/NodeState.kt`

- [ ] **Step 1: Update langchain4j NodeState.kt**

Replace `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/NodeState.kt` with:

```kotlin
package me.ahoo.cobal.langchain4j

import me.ahoo.cobal.DefaultCircuitBreaker
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.time.Duration

fun <NODE : Node> NODE.toNodeState(
    circuitOpenThreshold: Int = 5,
    circuitBreakerRecoveryDuration: Duration = Duration.ofSeconds(60),
): DefaultNodeState<NODE> = DefaultNodeState(
    this,
    failurePolicy = LangChain4jFailurePolicy,
    circuitBreaker = DefaultCircuitBreaker(circuitOpenThreshold, circuitBreakerRecoveryDuration),
)

fun <NODE : Node> List<NODE>.toNodeStates(
    circuitOpenThreshold: Int = 5,
    circuitBreakerRecoveryDuration: Duration = Duration.ofSeconds(60),
): List<NodeState<NODE>> = map { it.toNodeState(circuitOpenThreshold, circuitBreakerRecoveryDuration) }
```

- [ ] **Step 2: Update spring-ai NodeState.kt**

Replace `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/NodeState.kt` with:

```kotlin
package me.ahoo.cobal.springai

import me.ahoo.cobal.DefaultCircuitBreaker
import me.ahoo.cobal.DefaultNodeState
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.time.Duration

fun <NODE : Node> NODE.toNodeState(
    circuitOpenThreshold: Int = 5,
    circuitBreakerRecoveryDuration: Duration = Duration.ofSeconds(60),
): DefaultNodeState<NODE> = DefaultNodeState(
    this,
    failurePolicy = SpringAiFailurePolicy,
    circuitBreaker = DefaultCircuitBreaker(circuitOpenThreshold, circuitBreakerRecoveryDuration),
)

fun <NODE : Node> List<NODE>.toNodeStates(
    circuitOpenThreshold: Int = 5,
    circuitBreakerRecoveryDuration: Duration = Duration.ofSeconds(60),
): List<NodeState<NODE>> = map { it.toNodeState(circuitOpenThreshold, circuitBreakerRecoveryDuration) }
```

- [ ] **Step 3: Verify both modules compile**

Run: `./gradlew :langchain4j:compileKotlin :spring-ai:compileKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/NodeState.kt spring-ai/src/main/kotlin/me/ahoo/cobal/springai/NodeState.kt
git commit -m "refactor: update integration module factory functions for CircuitBreaker"
```

---

### Task 6: Full verification

- [ ] **Step 1: Run all core tests**

Run: `./gradlew :core:test 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all langchain4j tests**

Run: `./gradlew :langchain4j:test 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all spring-ai tests**

Run: `./gradlew :spring-ai:test 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run detekt**

Run: `./gradlew detekt 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run full build**

Run: `./gradlew build 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL
