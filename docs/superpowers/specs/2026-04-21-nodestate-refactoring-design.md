# NodeState Refactoring Design

## Goal

Refactor `DefaultNodeState` to use a unified `NodeStat` data class with `AtomicReference` for thread safety, replace passive time-checking with coroutine-scheduled recovery, and simplify the `status` property.

## NodeStat — Unified State

```kotlin
data class NodeStat(
    val failureCount: Int = 0,
    val circuitOpened: Boolean = false,
    val status: NodeStatus = NodeStatus.AVAILABLE,
)
```

All mutable state held in a single `AtomicReference<NodeStat>`. State transitions via CAS `update` — no `synchronized`.

`status` becomes a direct read from `AtomicReference`:

```kotlin
override val status: NodeStatus
    get() = stat.get().status
```

`available` remains derived: `AVAILABLE || CIRCUIT_HALF_OPEN`.

`recoverAt` is **not stored** in `NodeStat`. Recovery timing is managed by scheduled coroutines.

## Scheduled Recovery

When `NodeFailurePolicy` returns a `NodeFailureDecision`, launch a coroutine to transition state after the delay:

```kotlin
private var recoveryJob: Job? = null

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
```

`recover()` performs a CAS update:
- `UNAVAILABLE` → `AVAILABLE`
- `CIRCUIT_OPEN` → `CIRCUIT_HALF_OPEN`
- Emits `NodeEvent.Recovered`

## onFailure

```kotlin
override fun onFailure(error: CobalError) {
    val decision = failurePolicy.evaluate(error)
    var becameCircuitOpen = false
    var recoveredAt: Instant? = null

    stat.update { current ->
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
```

## onSuccess

```kotlin
override fun onSuccess() {
    stat.update { current ->
        when (current.status) {
            NodeStatus.CIRCUIT_HALF_OPEN,
            NodeStatus.UNAVAILABLE -> NodeStat()
            else -> current.copy(failureCount = 0)
        }
    }
    recoveryJob?.cancel()
    events.tryEmit(NodeEvent.Recovered(node.id))
}
```

## Constructor

```kotlin
class DefaultNodeState<NODE : Node>(
    override val node: NODE,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val failurePolicy: NodeFailurePolicy = NodeFailurePolicy.Default,
    private val circuitOpenThreshold: Int = 5,
) : NodeState<NODE>
```

**Removed**: `clock: Clock` — no longer needed.
**Added**: `scope: CoroutineScope` — with default value `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.

## Impact on Callers

- `NodeState<NODE>` interface — no signature changes.
- `LoadBalancer` implementations that create `DefaultNodeState` — add `scope` parameter (or rely on default).
- Integration modules (langchain4j, spring-ai) — `LoadBalanced*Model` constructors may need `scope` propagation.
- Tests — replace `MutableClock` with `TestScope` + `advanceTimeBy`; replace `thread` + `CountDownLatch` with coroutine-based concurrency tests.

## Files Changed

- `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt` — main refactor
- `core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt` — test rewrite
- Integration module tests — update `DefaultNodeState` construction where applicable