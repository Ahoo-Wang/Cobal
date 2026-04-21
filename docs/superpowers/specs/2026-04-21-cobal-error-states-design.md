# Cobal 异常与 LoadBalancer 变更设计

## 变更 1: LoadBalancer.states

### 目标

将 `LoadBalancer.nodes` 改为 `states`，让调用方直接传入 `NodeState` 列表，职责更清晰。

### 设计

```kotlin
interface LoadBalancer<NODE : Node> {
    val id: LoadBalancerId
    val states: List<NodeState<NODE>>           // 直接持有 NodeState 列表
    fun availableStates(): List<NodeState<NODE>> // 过滤可用节点
    fun choose(): NodeState<NODE>
}
```

调用方负责创建 `NodeState` 并传入 LoadBalancer，LoadBalancer 不再自己创建 `NodeState`。

### 实现类变化

**RandomLoadBalancer:**

```kotlin
class RandomLoadBalancer<NODE : Node>(
    override val id: LoadBalancerId,
    override val states: List<NodeState<NODE>>
) : LoadBalancer<NODE> {

    override fun availableStates(): List<NodeState<NODE>> {
        return states.filter { it.available }
    }

    override fun choose(): NodeState<NODE> {
        val available = availableStates()
        if (available.isEmpty()) throw AllNodesUnavailableError(id)
        return available[ThreadLocalRandom.current().nextInt(available.size)]
    }
}
```

RoundRobin 和 WeightedRoundRobin 同理。

---

## 变更 2: 统一异常体系

### 目标

定义层次化的异常体系：`CobalError` → `NodeError` → 具体错误类，支持 `RetriableError` 标记可恢复异常。

### 设计

```kotlin
// 基类
abstract class CobalError(message: String?, cause: Throwable?) : RuntimeException(message, cause)

// 可恢复异常标记接口
interface RetriableError

// NodeError 记录 nodeId
open class NodeError(
    val nodeId: NodeId,
    message: String?,
    cause: Throwable?
) : CobalError(message, cause)

// 具体错误类 - message 包含 nodeId
class RateLimitError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Rate limited [$nodeId]", cause), RetriableError
class ServerError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Server error [$nodeId]", cause), RetriableError
class TimeoutError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Timeout [$nodeId]", cause), RetriableError
class NetworkError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Network error [$nodeId]", cause), RetriableError
class AuthenticationError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Auth failed [$nodeId]", cause)
class InvalidRequestError(nodeId: NodeId, cause: Throwable?) : NodeError(nodeId, "Invalid request [$nodeId]", cause)

// 不可恢复
class AllNodesUnavailableError(loadBalancerId: LoadBalancerId) : CobalError("All nodes unavailable: $loadBalancerId", null)

// NodeFailurePolicy
fun interface NodeFailurePolicy {
    fun evaluate(error: CobalError): RetriableError?
}
```

### 异常层次

```
CobalError (RuntimeException)
├── NodeError (记录 nodeId)
│   ├── RateLimitError : RetriableError ✓
│   ├── ServerError : RetriableError ✓
│   ├── TimeoutError : RetriableError ✓
│   ├── NetworkError : RetriableError ✓
│   ├── AuthenticationError (不可恢复)
│   └── InvalidRequestError (不可恢复)
└── AllNodesUnavailableError (不可恢复)
```

### NodeState.onFailure 变化

```kotlin
override fun onFailure(error: CobalError) {
    failurePolicy.evaluate(error)?.let { retriable ->
        this.recoverAt = Instant.now() + Duration.ofSeconds(30)  // 或从异常获取
        events.tryEmit(NodeEvent.MarkedUnavailable(node.id, recoverAt))
    }
}
```

---

## 受影响文件

### Core 模块
- `LoadBalancer.kt` - 接口变更
- `CobalError.kt` - 异常体系
- `NodeState.kt` - onFailure 签名变更
- `algorithm/RandomLoadBalancer.kt`
- `algorithm/RoundRobinLoadBalancer.kt`
- `algorithm/WeightedRoundRobinLoadBalancer.kt`

### LangChain4j 模块
- `LoadBalanced*Model.kt` - 使用新异常体系

### Spring AI 模块
- `LoadBalanced*Model.kt` - 使用新异常体系

---

## 验证

```bash
./gradlew :core:test :langchain4j:test :spring-ai:test
./gradlew build
```
