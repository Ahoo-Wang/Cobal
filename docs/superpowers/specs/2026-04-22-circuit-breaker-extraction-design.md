# CircuitBreaker Extraction Design

## Context

`DefaultNodeState` currently embeds circuit breaker logic (failure threshold counting, OPEN/HALF_OPEN transitions, recovery timing) alongside policy-driven UNAVAILABLE transitions. This coupling makes it impossible to swap circuit breaker strategies (e.g. sliding window, adaptive) without modifying NodeState itself.

This refactoring extracts the circuit breaker algorithm into a standalone `CircuitBreaker` interface with a `DefaultCircuitBreaker` implementation, while NodeState retains only the policy-driven UNAVAILABLE logic.

## Scope

- **In scope**: Extract CIRCUIT_OPEN/CIRCUIT_HALF_OPEN state machine into `CircuitBreaker` interface. Implement `DefaultCircuitBreaker`. Refactor `DefaultNodeState` to delegate. Update integration modules and tests.
- **Out of scope**: UNAVAILABLE/policy-driven transitions remain in NodeState. No new circuit breaker strategies. No changes to LoadBalancer or algorithm selection.

## New Types

### CircuitBreakerState enum

```kotlin
enum class CircuitBreakerState {
    CLOSED,    // healthy, counting failures
    OPEN,      // tripped, ineligible for traffic
    HALF_OPEN  // probing recovery, eligible for one request
}
```

Standard circuit breaker naming (CLOSED/OPEN/HALF_OPEN). Maps to existing `NodeStatus.CIRCUIT_OPEN` and `NodeStatus.CIRCUIT_HALF_OPEN`.

### CircuitBreakerTransition sealed interface

```kotlin
sealed interface CircuitBreakerTransition {
    data object Opened : CircuitBreakerTransition       // CLOSED -> OPEN
    data object ReHalfOpened : CircuitBreakerTransition // HALF_OPEN -> OPEN
    data object HalfOpened : CircuitBreakerTransition   // OPEN -> HALF_OPEN (via tryRecover)
    data object Closed : CircuitBreakerTransition       // HALF_OPEN -> CLOSED
}
```

Return values from `CircuitBreaker.onError()`/`onSuccess()`/`tryRecover()`. Null means no state change.

### CircuitBreaker interface

```kotlin
interface CircuitBreaker {
    val state: CircuitBreakerState
    val recoverAt: Instant?

    fun onError(): CircuitBreakerTransition?
    fun onSuccess(): CircuitBreakerTransition?
    fun tryRecover(): CircuitBreakerTransition?
}
```

- `onError()` — CLOSED: increment count, open at threshold. HALF_OPEN: re-open immediately. OPEN: no change.
- `onSuccess()` — HALF_OPEN: close and reset. CLOSED: reset failure count (return null).
- `tryRecover()` — OPEN: transition to HALF_OPEN. Called by NodeState after recovery timer fires.
- `recoverAt` — computed from `openedAt + recoveryDuration`. Null when CLOSED or HALF_OPEN.

### DefaultCircuitBreaker

```kotlin
class DefaultCircuitBreaker(
    private val threshold: Int = 5,
    private val recoveryDuration: Duration = Duration.ofSeconds(60),
) : CircuitBreaker
```

Thread-safe via `AtomicReference<CircuitBreakerStat>`. `CircuitBreakerStat` holds `failureCount`, `state`, and `openedAt`.

File: `core/src/main/kotlin/me/ahoo/cobal/CircuitBreaker.kt` (new file)

## Changes to Existing Types

### NodeState interface

Add one property:

```kotlin
interface NodeState<NODE : Node> {
    // ... existing properties unchanged ...
    val circuitBreaker: CircuitBreaker  // NEW
}
```

### NodeStatus enum

No changes. AVAILABLE, UNAVAILABLE, CIRCUIT_OPEN, CIRCUIT_HALF_OPEN remain as-is.

### NodeStat (internal)

Simplified — no longer tracks failure count or circuit-opened flag:

```kotlin
internal data class NodeStat(
    val nodeStatus: NodeStatus = NodeStatus.AVAILABLE,  // only AVAILABLE or UNAVAILABLE
)
```

### DefaultNodeState

Constructor changes:

```kotlin
// Before
class DefaultNodeState<NODE : Node>(
    node: NODE,
    scope: CoroutineScope = ...,
    failurePolicy: NodeFailurePolicy = Default,
    circuitOpenThreshold: Int = 5,  // REMOVED
)

// After
class DefaultNodeState<NODE : Node>(
    node: NODE,
    scope: CoroutineScope = ...,
    failurePolicy: NodeFailurePolicy = Default,
    override val circuitBreaker: CircuitBreaker = DefaultCircuitBreaker(),  // NEW
)
```

`status` is now computed from two sources:

```kotlin
override val status: NodeStatus
    get() = when (circuitBreaker.state) {
        CircuitBreakerState.OPEN -> NodeStatus.CIRCUIT_OPEN
        CircuitBreakerState.HALF_OPEN -> NodeStatus.CIRCUIT_HALF_OPEN
        CircuitBreakerState.CLOSED -> stat.get().nodeStatus
    }
```

`onError()` flow:
1. Evaluate `failurePolicy` -> decision
2. Call `circuitBreaker.onError()` -> CB transition
3. If decision != null: update NodeStat to UNAVAILABLE, emit MarkedUnavailable, schedule policy recovery
4. If CB transition is Opened/ReHalfOpened: emit CircuitOpened, schedule CB recovery using `circuitBreaker.recoverAt`

`onSuccess()` flow:
1. Call `circuitBreaker.onSuccess()` -> CB transition
2. If CB transition is Closed OR node was UNAVAILABLE: cancel recovery job, reset NodeStat, emit Recovered

`recover()` (called by timer):
1. Call `circuitBreaker.tryRecover()` -> CB transition
2. If CB transition (OPEN -> HALF_OPEN): emit CircuitHalfOpen
3. If node was UNAVAILABLE: reset NodeStat to AVAILABLE, emit Recovered

### Integration Module Factory Functions

`toNodeState()` in both langchain4j and spring-ai modules:

```kotlin
fun <NODE : Node> NODE.toNodeState(
    circuitOpenThreshold: Int = 5,
    circuitBreakerRecoveryDuration: Duration = Duration.ofSeconds(60),
): DefaultNodeState<NODE> = DefaultNodeState(
    this,
    failurePolicy = ModulePolicy,
    circuitBreaker = DefaultCircuitBreaker(circuitOpenThreshold, circuitBreakerRecoveryDuration),
)
```

## Behavioral Changes

| Aspect | Before | After |
|--------|--------|-------|
| CB recovery timing | Derived from NodeFailurePolicy (30s for RateLimitError) | Independent `recoveryDuration` on DefaultCircuitBreaker (default 60s) |
| Non-policy CB opens | No automatic recovery timer | Recovery timer always scheduled when circuit opens |
| CB state visibility | Hidden in NodeStat | Exposed via `circuitBreaker` property |

The second item is a bug fix: currently, if the circuit opens due to non-policy errors (e.g. ServerError), no recovery timer is scheduled. After this refactoring, the CB always schedules its own recovery.

## Files to Create

1. `core/src/main/kotlin/me/ahoo/cobal/CircuitBreaker.kt` — CircuitBreakerState, CircuitBreakerTransition, CircuitBreaker, DefaultCircuitBreaker, CircuitBreakerStat

## Files to Modify

1. `core/src/main/kotlin/me/ahoo/cobal/NodeState.kt` — Add `circuitBreaker` to NodeState interface, refactor DefaultNodeState, simplify NodeStat
2. `core/src/test/kotlin/me/ahoo/cobal/NodeStateTest.kt` — Update constructor calls, add CB-specific tests
3. `core/src/test/kotlin/me/ahoo/cobal/AbstractLoadBalancedModelTest.kt` — Update DefaultNodeState constructors if needed
4. `langchain4j/src/main/kotlin/me/ahoo/cobal/langchain4j/NodeState.kt` — Update factory function
5. `spring-ai/src/main/kotlin/me/ahoo/cobal/springai/NodeState.kt` — Update factory function
6. `langchain4j/src/test/kotlin/...` — Update DefaultNodeState constructors
7. `spring-ai/src/test/kotlin/...` — Update DefaultNodeState constructors

## Verification

1. `./gradlew :core:test` — All core tests pass including new CircuitBreaker tests
2. `./gradlew :langchain4j:test` — LangChain4j integration tests pass
3. `./gradlew :spring-ai:test` — Spring AI integration tests pass
4. `./gradlew detekt` — No new code quality issues
5. Manual review: thread-safety of DefaultCircuitBreaker (AtomicReference pattern matches existing NodeStat approach)
