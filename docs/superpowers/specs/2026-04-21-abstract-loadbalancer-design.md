# AbstractLoadBalancer Design

## Summary

Introduce `AbstractLoadBalancer` as a base class for all algorithm-specific load balancer implementations. It eliminates duplicated boilerplate (property declarations and empty-availability checks) across `RandomLoadBalancer`, `RoundRobinLoadBalancer`, and `WeightedRoundRobinLoadBalancer` by using the Template Method pattern.

## Motivation

All three existing algorithm implementations share identical code:

1. Declaring `override val id` and `override val states` constructor parameters.
2. Checking `available.isEmpty()` and throwing `AllNodesUnavailableError` inside `choose()`.

Extracting this repetition into an abstract base class reduces duplication and makes future algorithm additions simpler.

## Architecture

```
LoadBalancer (interface)
    ↑
AbstractLoadBalancer (abstract class)
    ↑
RandomLoadBalancer ──┐
RoundRobinLoadBalancer ─┤── concrete algorithms
WeightedRoundRobinLoadBalancer ──┘
```

- `LoadBalancer` remains an interface — consumers continue to depend on the abstraction.
- `AbstractLoadBalancer` lives in the `me.ahoo.cobal` package alongside the interface.
- Concrete algorithm classes move from implementing the interface directly to extending the abstract class.

## AbstractLoadBalancer Definition

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

### Design Decisions

- `choose()` is `final` so the empty-availability guard cannot be accidentally skipped by subclasses.
- `doChoose()` receives only the pre-filtered `available` list, letting subclasses focus purely on selection logic.
- `availableStates` stays as the interface default property — no need to override it in the abstract class.

## Subclass Changes

### RoundRobinLoadBalancer

```kotlin
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

### RandomLoadBalancer

```kotlin
class RandomLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : AbstractLoadBalancer<NODE>(id, states) {

    override fun doChoose(available: List<NodeState<NODE>>): NodeState<NODE> {
        return available[ThreadLocalRandom.current().nextInt(available.size)]
    }
}
```

### WeightedRoundRobinLoadBalancer

```kotlin
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

## Files Changed

| Action | File |
|--------|------|
| Create | `core/src/main/kotlin/me/ahoo/cobal/AbstractLoadBalancer.kt` |
| Modify | `core/src/main/kotlin/me/ahoo/cobal/algorithm/RandomLoadBalancer.kt` |
| Modify | `core/src/main/kotlin/me/ahoo/cobal/algorithm/RoundRobinLoadBalancer.kt` |
| Modify | `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt` |

## Backward Compatibility

- Constructor signatures of all three algorithm classes remain unchanged.
- `choose()` behavior (return type, exception type, timing) is identical.
- All existing tests compile and pass without modification.
- `LoadBalancerRegistry` and integration modules are unaffected because they depend on the `LoadBalancer` interface, not concrete classes.

## Testing Strategy

1. **Existing tests** — run as-is to verify zero behavioral regression.
2. **New unit tests for AbstractLoadBalancer** — verify that `choose()` throws `AllNodesUnavailableError` when `availableStates` is empty, and delegates to `doChoose` otherwise.
