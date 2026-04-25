# WeightedRoundRobinLoadBalancer Design

## Overview

Add a `WeightedRoundRobinLoadBalancer` algorithm to `core/.../algorithm/`, using Smooth Weighted Round Robin (Nginx-style) for precise weighted round-robin selection with lock-free concurrency.

## Algorithm

**Smooth Weighted Round Robin** maintains a `currentWeight` per node. On each selection:

1. For each candidate node: `currentWeight[i] += weight[i]`
2. Select the node with the highest `currentWeight`
3. Subtract `totalWeight` from the selected node's `currentWeight`

This produces a smooth, deterministic distribution. For weights 5:1:1, the selection pattern over 7 calls is: A, A, A, A, A, B, C — no consecutive bursts of B or C.

## Class Design

```
WeightedRoundRobinLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : AbstractLoadBalancer<NODE>(id, states)
```

- `currentWeightsRef: AtomicReference<IntArray>` — per-node current weights, rebuilt on state change
- `totalWeightRef: AtomicReference<Int>` — sum of available node weights, rebuilt on state change
- Overrides `onStateChanged()` to reset currentWeights to zeros and recalculate totalWeight
- Overrides `doChoose()` to perform smooth WRR selection

## Concurrency

- `AtomicReference<IntArray>` for currentWeights — each `doChoose()` call reads the array, computes the selection with a compare-and-swap loop to update weights atomically
- `AtomicReference<Int>` for totalWeight — read atomically during selection
- `onStateChanged()` rebuilds both atomically
- Lock-free: no `@Synchronized` or `ReentrantLock`

## Edge Cases

- **Single available node**: return directly, skip algorithm
- **All nodes unavailable**: handled by parent `choose()` throwing `AllNodesUnavailableError`
- **Node state change**: `onStateChanged()` resets currentWeights to all zeros and recalculates totalWeight. This avoids stale indices.

## Files to Create/Modify

1. `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancer.kt` — new file
2. `core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRoundRobinLoadBalancerTest.kt` — new file

## Tests

- `weighted round robin should respect node weights` — 12 calls with 3:1 weights = 9:3
- `choose should skip unavailable node` — node goes unavailable, remaining gets all traffic
- `choose should throw AllNodesUnavailableError when no nodes available`
- `concurrent choose should be thread-safe` — multi-threaded, no errors, correct total
