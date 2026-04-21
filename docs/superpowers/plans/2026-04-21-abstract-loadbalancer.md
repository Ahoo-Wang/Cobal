# AbstractLoadBalancer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce `AbstractLoadBalancer` as a template-method base class to eliminate duplicated `id`/`states` declarations and empty-availability checks across `RandomLoadBalancer`, `RoundRobinLoadBalancer`, and `WeightedRoundRobinLoadBalancer`.

**Architecture:** A single abstract class in `me.ahoo.cobal` implements `LoadBalancer` with a `final choose()` that guards against empty availability and delegates to `protected abstract doChoose()`. All three algorithm classes extend it instead of implementing the interface directly.

**Tech Stack:** Kotlin 2.3.20, JUnit Jupiter, MockK, fluent-assert

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `core/src/main/kotlin/me/ahoo/cobal/AbstractLoadBalancer.kt` | Create | Template-method base class with `choose()` guard and abstract `doChoose()` |
| `core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt` | Modify | Extend `AbstractLoadBalancer`, implement `doChoose()` only |
| `core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt` | Modify | Extend `AbstractLoadBalancer`, implement `doChoose()` only |
| `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt` | Modify | Extend `AbstractLoadBalancer`, implement `doChoose()` only |
| `core/src/test/kotlin/me/ahoo/cobal/AbstractLoadBalancerTest.kt` | Create | Unit tests for the template method: empty-guard throws, non-empty delegates |

---

### Task 1: Create AbstractLoadBalancer.kt

**Files:**
- Create: `core/src/main/kotlin/me/ahoo/cobal/AbstractLoadBalancer.kt`

- [ ] **Step 1: Write the abstract class**

```kotlin
package me.ahoo.cobal

abstract class AbstractLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    final override fun choose(): NodeState<NODE> {
        val available = availableStates
        if (available.isEmpty()) {
            throw AllNodesUnavailableError(id)
        }
        return doChoose(available)
    }

    protected abstract fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE>
}
```

- [ ] **Step 2: Compile to verify no syntax errors**

Run: `./gradlew :core:compileKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 2: Refactor RandomLoadBalancer

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt`

- [ ] **Step 1: Replace implementation with AbstractLoadBalancer extension**

The entire file becomes:

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AbstractLoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.util.concurrent.ThreadLocalRandom

class RandomLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : AbstractLoadBalancer<NODE>(id, states) {

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        return available[ThreadLocalRandom.current().nextInt(available.size)]
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :core:compileKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 3: Refactor RoundRobinLoadBalancer

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt`

- [ ] **Step 1: Replace implementation with AbstractLoadBalancer extension**

The entire file becomes:

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AbstractLoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeState
import java.util.concurrent.atomic.AtomicInteger

class RoundRobinLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : AbstractLoadBalancer<NODE>(id, states) {

    private val index = AtomicInteger(0)

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        val startIndex = index.getAndIncrement() % available.size
        return available[startIndex]
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :core:compileKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 4: Refactor WeightedRoundRobinLoadBalancer

**Files:**
- Modify: `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt`

- [ ] **Step 1: Replace implementation with AbstractLoadBalancer extension**

The entire file becomes:

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.AbstractLoadBalancer
import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.NodeId
import me.ahoo.cobal.NodeState

class WeightedRoundRobinLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : AbstractLoadBalancer<NODE>(id, states) {

    private var currentIndex = 0
    private var currentWeight: Int
    private val maxWeight: Int = states.maxOf { it.node.weight }
    private val weightMap: Map<NodeId, Int> = states.associate { it.node.id to it.node.weight }

    init {
        currentWeight = maxWeight
    }

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
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

- [ ] **Step 2: Compile**

Run: `./gradlew :core:compileKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 5: Run Existing Algorithm Tests (Regression)

**Files:** none changed

- [ ] **Step 1: Run all algorithm tests**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.*"`
Expected: All 3 test classes pass (RandomLoadBalancerTest, RoundRobinLoadBalancerTest, WeightedRoundRobinLoadBalancerTest)

---

### Task 6: Add AbstractLoadBalancer Unit Tests

**Files:**
- Create: `core/src/test/kotlin/me/ahoo/cobal/AbstractLoadBalancerTest.kt`

- [ ] **Step 1: Write the test class**

```kotlin
package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AbstractLoadBalancerTest {

    private class FixedLoadBalancer<NODE : Node>(
        id: LoadBalancerId,
        states: List<NodeState<NODE>>,
        private val fixed: NodeState<NODE>
    ) : AbstractLoadBalancer<NODE>(id, states) {
        override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> = fixed
    }

    @Test
    fun `choose should delegate to doChoose when nodes available`() {
        val node = SimpleNode("node-1")
        val state = DefaultNodeState(node)
        val lb = FixedLoadBalancer("test-lb", listOf(state), state)
        lb.choose().assert().isEqualTo(state)
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when no nodes available`() {
        val node = SimpleNode("node-1")
        val state = DefaultNodeState(node)
        val error = RateLimitError(node.id, RuntimeException("429"))
        state.onFailure(error)

        val lb = FixedLoadBalancer("test-lb", listOf(state), state)
        val ex = assertThrows<AllNodesUnavailableError> { lb.choose() }
        ex.loadBalancerId.assert().isEqualTo("test-lb")
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when states empty`() {
        val lb = FixedLoadBalancer<SimpleNode>("empty-lb", emptyList())
        val ex = assertThrows<AllNodesUnavailableError> { lb.choose() }
        ex.loadBalancerId.assert().isEqualTo("empty-lb")
    }
}
```

注意：`FixedLoadBalancer` 的第三个构造参数在 empty-list 测试中不需要，可以提供一个无 `fixed` 的备用构造函数，或者直接用两个不同的内部类。更简洁的做法是提供两个 test double：

修正后的测试：

```kotlin
package me.ahoo.cobal

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AbstractLoadBalancerTest {

    private class FixedLoadBalancer<NODE : Node>(
        id: LoadBalancerId,
        states: List<NodeState<NODE>>,
        private val fixed: NodeState<NODE>
    ) : AbstractLoadBalancer<NODE>(id, states) {
        override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> = fixed
    }

    @Test
    fun `choose should delegate to doChoose when nodes available`() {
        val node = SimpleNode("node-1")
        val state = DefaultNodeState(node)
        val lb = FixedLoadBalancer("test-lb", listOf(state), state)
        lb.choose().assert().isEqualTo(state)
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when no nodes available`() {
        val node = SimpleNode("node-1")
        val state = DefaultNodeState(node)
        val error = RateLimitError(node.id, RuntimeException("429"))
        state.onFailure(error)

        val lb = FixedLoadBalancer("test-lb", listOf(state), state)
        val ex = assertThrows<AllNodesUnavailableError> { lb.choose() }
        ex.loadBalancerId.assert().isEqualTo("test-lb")
    }

    @Test
    fun `choose should throw AllNodesUnavailableError when states empty`() {
        val lb = object : AbstractLoadBalancer<SimpleNode>("empty-lb", emptyList()) {
            override fun doChoose(available: List<NodeState<SimpleNode>>): NodeState<SimpleNode> {
                throw AssertionError("doChoose should not be called when no nodes available")
            }
        }
        val ex = assertThrows<AllNodesUnavailableError> { lb.choose() }
        ex.loadBalancerId.assert().isEqualTo("empty-lb")
    }
}
```

- [ ] **Step 2: Run the new tests**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.AbstractLoadBalancerTest"`
Expected: All 3 tests pass

---

### Task 7: Full Test Suite + Commit

- [ ] **Step 1: Run full core test suite**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Run Detekt**

Run: `./gradlew :core:detekt`
Expected: BUILD SUCCESSFUL (no new style violations)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/AbstractLoadBalancer.kt \
        core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt \
        core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt \
        core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt \
        core/src/test/kotlin/me/ahoo/cobal/AbstractLoadBalancerTest.kt
git commit -m "feat(core): add AbstractLoadBalancer base class with template method

- Extract common id/states properties and empty-availability guard
- RandomLoadBalancer, RoundRobinLoadBalancer, WeightedRoundRobinLoadBalancer
  now extend AbstractLoadBalancer and implement doChoose() only

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** Every spec requirement (abstract class creation, template method, three subclass refactors, backward compatibility, tests) maps to a task.
- [x] **Placeholder scan:** No TBD, TODO, or vague steps. Every code block is complete and runnable.
- [x] **Type consistency:** `AbstractLoadBalancer<NODE : Node>`, `doChoose(available: List<NodeState<NODE>>)` used consistently across all tasks.
