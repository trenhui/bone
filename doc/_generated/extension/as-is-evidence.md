# 扩展模块 As-Is 证据（CI 派生）

> **生成时间**：2026-05-27T02:13:41Z（UTC）  
> **勿手改**：由 `tools/extension-compliance-collector/collect.py` 生成。

| ID | 能力 | 证据摘要 |
|----|------|----------|
| `delete-204` | DELETE 返回 204 无 body | OpenAPI DELETE 204：2 处；`noContent()`：2 文件 |
| `idempotency-409` | 幂等键冲突 409 COMMON_IDEMPOTENCY_CONFLICT | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioIdempotencyService.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/common/StudioErrorCodes.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/common/exception/IdempotencyConflictException.java`；… |
| `archunit-studio` | Studio ArchUnit 分层守护 | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/test/java/com/bone/engine/extension/studio/architecture/ArchitectureTest.java` |
| `execution-guard` | SDK 执行舱壁与超时 | 源码：`bone-engine/bone-extension-engine/bone-extension-sdk/src/main/java/com/bone/engine/extension/core/executor/ExtensionExecutionGuard.java` |
| `lro-deploy` | LRO 部署 operationId | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioLroService.java` |
| `idempotency-key` | Idempotency-Key（进程内） | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioIdempotencyService.java` |

## 明细

### `delete-204` — DELETE 返回 204 无 body

```json
{
  "openapi": {
    "openapi_file": "doc/architecture/openapi/extension-v1.yaml",
    "delete_operations_with_204": 2,
    "paths": [
      "/points/{id}",
      "/plugins/{id}"
    ]
  },
  "java_noContent": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtPointCommandHandler.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtensionStudioCommandHandler.java"
  ]
}
```

### `idempotency-409` — 幂等键冲突 409 COMMON_IDEMPOTENCY_CONFLICT

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioIdempotencyService.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/common/StudioErrorCodes.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/common/exception/IdempotencyConflictException.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/config/StudioWebExceptionHandler.java"
  ]
}
```

### `archunit-studio` — Studio ArchUnit 分层守护

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/test/java/com/bone/engine/extension/studio/architecture/ArchitectureTest.java"
  ]
}
```

### `execution-guard` — SDK 执行舱壁与超时

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-sdk/src/main/java/com/bone/engine/extension/core/executor/ExtensionExecutionGuard.java"
  ]
}
```

### `lro-deploy` — LRO 部署 operationId

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioLroService.java"
  ]
}
```

### `idempotency-key` — Idempotency-Key（进程内）

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioIdempotencyService.java"
  ]
}
```
