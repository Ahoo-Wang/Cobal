# WeightedRandomLoadBalancer Design

## Overview

Add a `WeightedRandomLoadBalancer` algorithm to `core/.../algorithm/`, using Vose's Alias Method for O(1) weighted random selection with O(n) preprocessing.

## Algorithm

**Vose's Alias Method** builds two arrays (`prob` and `alias`) from the weight distribution. Selection is O(1): pick a random slot, compare against the stored probability threshold, and optionally use the alias index.

**Preprocessing (buildAliasTable)**:
1. Normalize weights to probabilities (`weight / totalWeight`)
2. Partition into `small` (prob < 1/n) and `large` (prob >= 1/n) stacks
3. Pop from both: set `prob[i] = small.prob * n`, `alias[i] = large.index`, subtract the remainder from large, re-queue if still >= 1/n
4. Remaining large entries get `prob = 1.0`

**Selection (doChoose)**:
1. Generate random slot index via `ThreadLocalRandom`
2. Generate random threshold in [0, 1)
3. If threshold < `prob[slot]`, return `available[slot]`
4. Else return `available[alias[slot]]`

## Class Design

```
WeightedRandomLoadBalancer<NODE : Node>(
    id: LoadBalancerId,
    states: List<NodeState<NODE>>
) : AbstractLoadBalancer<NODE>(id, states)
```

- `aliasTable`: `AtomicReference<AliasTable?>` — cached alias table, rebuilt on state change
- `AliasTable` — internal data class holding `prob: DoubleArray` and `alias: IntArray`
- Overrides `onStateChanged()` to rebuild the alias table
- Overrides `doChoose()` to perform O(1) weighted random selection

## Edge Cases

- **Single available node**: skip alias table, return directly
- **Equal weights**: alias table degenerates to uniform random (correct behavior)
- **Zero available nodes**: handled by parent `choose()` throwing `AllNodesUnavailableError`
- **Zero-weight nodes**: excluded from selection by the alias table construction (if all weights are 0, treated as no available nodes)

## Concurrency

- `AtomicReference<AliasTable?>` for lock-free reads
- Rebuild on `onStateChanged()` — already called from the state transition listener, same pattern as parent's `availableStates` cache
- `ThreadLocalRandom` for thread-safe random number generation

## Files to Create/Modify

1. `core/src/main/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancer.kt` — new file
2. `core/src/test/kotlin/me/ahoo/cobal/algorithm/WeightedRandomLoadBalancerTest.kt` — new file

## Tests

- `choose should return single available node` — single node always selected
- `choose should distribute according to weights` — statistical test: 3 nodes with weights 1:2:3, run 6000 selections, verify distribution within tolerance
- `choose should rebuild alias table on state change` — mark a node unavailable, verify selection excludes it
- `choose should throw AllNodesUnavailableError when no nodes available` — all nodes circuit-open
- `choose should handle equal weight nodes` — uniform distribution verification
