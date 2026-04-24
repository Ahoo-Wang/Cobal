# WeightedRandomLoadBalancer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a WeightedRandomLoadBalancer using Vose's Alias Method for O(1) weighted random selection.

**Architecture:** Extends `AbstractLoadBalancer<NODE>`, same pattern as `RandomLoadBalancer` and `RoundRobinLoadBalancer`. Maintains an `AtomicReference<AliasTable>` rebuilt on `onStateChanged()`. The `AliasTable` holds `prob: DoubleArray` and `alias: IntArray` for O(1) lookup.

**Tech Stack:** Kotlin 2.3.20, JVM 17, Resilience4j CircuitBreaker, JUnit 5, fluent-assert

---

### Task 1: Alias Table Data Class

**Files:**
- Create: `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancer.kt`

- [ ] **Step 1: Write the failing test — single node selection**

Create the test file with the first test. This test verifies the simplest case: a single-node load balancer always returns that node.

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.DefaultNode
import me.ahoo.cobal.state.DefaultNodeState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class WeightedRandomLoadBalancerTest {
    @Test
    fun `choose should return single available node`() {
        val node = DefaultNode("node-1", weight = 5)
        val state = DefaultNodeState(node)
        val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state))
        repeat(10) {
            lb.choose().node.id.assert().isEqualTo("node-1")
        }
    }
}
```

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRandomLoadBalancerTest.choose should return single available node"`
Expected: FAIL — `WeightedRandomLoadBalancer` does not exist.

- [ ] **Step 2: Write minimal implementation**

Create `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancer.kt`:

```kotlin
package me.ahoo.cobal.algorithm

import me.ahoo.cobal.LoadBalancerId
import me.ahoo.cobal.Node
import me.ahoo.cobal.state.NodeState
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

class WeightedRandomLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>,
) : AbstractLoadBalancer<NODE>(id, states) {

    private val aliasTableRef = AtomicReference<AliasTable?>(null)

    private data class AliasTable(
        val prob: DoubleArray,
        val alias: IntArray,
    )

    init {
        rebuildAliasTable()
    }

    override fun onStateChanged() {
        rebuildAliasTable()
    }

    private fun rebuildAliasTable() {
        aliasTableRef.set(buildAliasTable(availableStates))
    }

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        val table = aliasTableRef.get()
        if (table == null || available.size == 1) {
            return available[0]
        }
        val random = ThreadLocalRandom.current()
        val slot = random.nextInt(available.size)
        return if (random.nextDouble() < table.prob[slot]) {
            available[slot]
        } else {
            available[table.alias[slot]]
        }
    }

    companion object {
        fun buildAliasTable(states: List<NodeState<*>>): AliasTable? {
            val n = states.size
            if (n <= 1) return null
            val prob = DoubleArray(n)
            val alias = IntArray(n)
            val totalWeight = states.sumOf { it.node.weight.toDouble() }
            if (totalWeight <= 0.0) return null

            val normalizedWeights = DoubleArray(n) { i -> states[i].node.weight.toDouble() * n / totalWeight }

            val small = ArrayDeque<Int>()
            val large = ArrayDeque<Int>()

            for (i in 0 until n) {
                if (normalizedWeights[i] < 1.0) small.addLast(i) else large.addLast(i)
            }

            while (small.isNotEmpty() && large.isNotEmpty()) {
                val s = small.removeFirst()
                val l = large.removeFirst()
                prob[s] = normalizedWeights[s]
                alias[s] = l
                normalizedWeights[l] += normalizedWeights[s] - 1.0
                if (normalizedWeights[l] < 1.0) small.addLast(l) else large.addLast(l)
            }

            while (large.isNotEmpty()) {
                prob[large.removeFirst()] = 1.0
            }
            while (small.isNotEmpty()) {
                prob[small.removeFirst()] = 1.0
            }

            return AliasTable(prob, alias)
        }
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRandomLoadBalancerTest.choose should return single available node"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancer.kt core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancerTest.kt
git commit -m "feat(loadbalancer): add WeightedRandomLoadBalancer with Alias Method"
```

---

### Task 2: Weight Distribution Test

**Files:**
- Modify: `core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancerTest.kt`

- [ ] **Step 1: Write the failing test — weight distribution**

Add this test to `WeightedRandomLoadBalancerTest`:

```kotlin
@Test
fun `choose should distribute according to weights`() {
    val node1 = DefaultNode("node-1", weight = 1)
    val node2 = DefaultNode("node-2", weight = 2)
    val node3 = DefaultNode("node-3", weight = 3)
    val state1 = DefaultNodeState(node1)
    val state2 = DefaultNodeState(node2)
    val state3 = DefaultNodeState(node3)
    val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state1, state2, state3))

    val counts = mutableMapOf("node-1" to 0, "node-2" to 0, "node-3" to 0)
    val iterations = 60_000
    repeat(iterations) {
        val chosen = lb.choose().node.id
        counts[chosen] = counts[chosen]!! + 1
    }

    // Expected: node-1 ≈ 10000 (1/6), node-2 ≈ 20000 (2/6), node-3 ≈ 30000 (3/6)
    // Tolerance: ±10%
    counts["node-1"]!!.assert().isBetween(9000, 11000)
    counts["node-2"]!!.assert().isBetween(18000, 22000)
    counts["node-3"]!!.assert().isBetween(27000, 33000)
}
```

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRandomLoadBalancerTest.choose should distribute according to weights"`
Expected: FAIL — test is new, will exercise the alias table. (Might actually PASS since the implementation is already complete. If it passes, skip step 2.)

- [ ] **Step 2: Verify it passes**

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRandomLoadBalancerTest.choose should distribute according to weights"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancerTest.kt
git commit -m "test(loadbalancer): add weight distribution test for WeightedRandomLoadBalancer"
```

---

### Task 3: State Change and Unavailability Tests

**Files:**
- Modify: `core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancerTest.kt`

- [ ] **Step 1: Write the failing tests — state change and all unavailable**

Add these tests to `WeightedRandomLoadBalancerTest`:

```kotlin
import me.ahoo.cobal.error.AllNodesUnavailableError
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import java.time.Duration

@Test
fun `choose should rebuild alias table on state change`() {
    val testConfig = CircuitBreakerConfig.custom()
        .failureRateThreshold(100.0f)
        .slidingWindowSize(1)
        .minimumNumberOfCalls(1)
        .waitDurationInOpenState(Duration.ofSeconds(60))
        .build()
    val node1 = DefaultNode("node-1", weight = 3)
    val node2 = DefaultNode("node-2", weight = 1)
    val state1 = DefaultNodeState(node1, io.github.resilience4j.circuitbreaker.CircuitBreaker.of("node-1", testConfig))
    val state2 = DefaultNodeState(node2, io.github.resilience4j.circuitbreaker.CircuitBreaker.of("node-2", testConfig))
    val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state1, state2))

    // Open node2's circuit breaker by recording a failure
    state2.circuitBreaker.onError(0, state2.circuitBreaker.timestampUnit, RuntimeException("error"))

    // Only node-1 should be selected now
    repeat(10) {
        lb.choose().node.id.assert().isEqualTo("node-1")
    }
}

@Test
fun `choose should throw AllNodesUnavailableError when no nodes available`() {
    val testConfig = CircuitBreakerConfig.custom()
        .failureRateThreshold(100.0f)
        .slidingWindowSize(1)
        .minimumNumberOfCalls(1)
        .waitDurationInOpenState(Duration.ofSeconds(60))
        .build()
    val node1 = DefaultNode("node-1", weight = 3)
    val state1 = DefaultNodeState(node1, io.github.resilience4j.circuitbreaker.CircuitBreaker.of("node-1", testConfig))
    val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state1))

    state1.circuitBreaker.onError(0, state1.circuitBreaker.timestampUnit, RuntimeException("error"))

    org.junit.jupiter.api.assertThrows<AllNodesUnavailableError> {
        lb.choose()
    }
}
```

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRandomLoadBalancerTest"`
Expected: PASS (implementation already handles these cases)

- [ ] **Step 2: Commit**

```bash
git add core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancerTest.kt
git commit -m "test(loadbalancer): add state change and unavailability tests for WeightedRandomLoadBalancer"
```

---

### Task 4: Equal Weight and Build Verification

**Files:**
- Modify: `core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancerTest.kt`

- [ ] **Step 1: Write equal-weight test and run full build**

Add this test to `WeightedRandomLoadBalancerTest`:

```kotlin
@Test
fun `choose should handle equal weight nodes`() {
    val node1 = DefaultNode("node-1", weight = 2)
    val node2 = DefaultNode("node-2", weight = 2)
    val state1 = DefaultNodeState(node1)
    val state2 = DefaultNodeState(node2)
    val lb = WeightedRandomLoadBalancer("wrandom-lb", listOf(state1, state2))

    val counts = mutableMapOf("node-1" to 0, "node-2" to 0)
    repeat(10_000) {
        val chosen = lb.choose().node.id
        counts[chosen] = counts[chosen]!! + 1
    }

    // With equal weights, both should be ~5000 ±10%
    counts["node-1"]!!.assert().isBetween(4500, 5500)
    counts["node-2"]!!.assert().isBetween(4500, 5500)
}
```

Run: `./gradlew :core:test --tests "me.ahoo.cobal.algorithm.WeightedRandomLoadBalancerTest"`
Expected: PASS

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: PASS (no violations)

- [ ] **Step 3: Commit**

```bash
git add core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancerTest.kt
git commit -m "test(loadbalancer): add equal weight distribution test for WeightedRandomLoadBalancer"
```
