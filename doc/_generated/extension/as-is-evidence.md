# 扩展模块 As-Is 证据（CI 派生）

> **生成时间**：2026-05-27T06:13:44Z（UTC）  
> **勿手改**：由 `tools/extension-compliance-collector/collect.py` 生成。

| ID | 能力 | 证据摘要 |
|----|------|----------|
| `delete-204` | DELETE 返回 204 无 body | OpenAPI DELETE 204：2 处；`noContent()`：2 文件 |
| `idempotency-409` | 幂等键冲突 409 COMMON_IDEMPOTENCY_CONFLICT | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioIdempotencyService.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/common/StudioErrorCodes.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/common/exception/IdempotencyConflictException.java`；… |
| `archunit-studio` | Studio ArchUnit 分层守护 | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/test/java/com/bone/engine/extension/studio/architecture/ArchitectureTest.java` |
| `execution-guard` | SDK 执行舱壁与超时 | 源码：`bone-engine/bone-extension-engine/bone-extension-sdk/src/main/java/com/bone/engine/extension/core/executor/ExtensionExecutionGuard.java` |
| `lro-deploy` | LRO 部署 operationId | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioLroService.java` |
| `idempotency-key` | Idempotency-Key（进程内） | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioIdempotencyService.java` |
| `if-match-412` | PUT If-Match / 412 Precondition Failed | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/adapter/web/controller/ExtensionManagementController.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/adapter/web/controller/StudioHttpSupport.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioCommandResponses.java`；… |
| `post-201-location` | POST 201 + Location | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtPointCommandHandler.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtensionStudioCommandHandler.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioCommandResponses.java` |
| `x-request-id` | X-Request-Id 回显 + traceId | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioAuditService.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioCommandResponses.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioLroService.java`；… |
| `patch-partial-update` | PATCH 部分更新（points/plugins） | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/adapter/web/controller/ExtensionManagementController.java` |
| `deployment-status-ddl` | deployment_status DDL + 状态机落库 | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtensionCommandHandler.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/domain/model/DeploymentStatus.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/domain/model/PluginVersion.java`；… |
| `mime-magic-number` | JAR magic-number 校验 | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/JarMagicValidator.java` |
| `artifact-download-auth-url` | 制品鉴权下载 GET :download | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/adapter/web/controller/ExtensionManagementController.java`, `bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtensionStudioCommandHandler.java` |
| `rollout-percent-traffic-merge` | rollout_percent ↔ config_json.traffic 收敛（ADR-0014） | 源码：`bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/infrastructure/persistence/converter/StudioPersistenceConverter.java` |

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

### `if-match-412` — PUT If-Match / 412 Precondition Failed

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/adapter/web/controller/ExtensionManagementController.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/adapter/web/controller/StudioHttpSupport.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioCommandResponses.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioVersionSupport.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/common/exception/OptimisticLockException.java"
  ]
}
```

### `post-201-location` — POST 201 + Location

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtPointCommandHandler.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtensionStudioCommandHandler.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioCommandResponses.java"
  ]
}
```

### `x-request-id` — X-Request-Id 回显 + traceId

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioAuditService.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioCommandResponses.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/StudioLroService.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/config/StudioRequestContextFilter.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/config/StudioWebExceptionHandler.java"
  ]
}
```

### `patch-partial-update` — PATCH 部分更新（points/plugins）

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/adapter/web/controller/ExtensionManagementController.java"
  ]
}
```

### `deployment-status-ddl` — deployment_status DDL + 状态机落库

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtensionCommandHandler.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/domain/model/DeploymentStatus.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/domain/model/PluginVersion.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/infrastructure/persistence/converter/StudioPersistenceConverter.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/infrastructure/persistence/entity/ExtStudioPluginVersion.java"
  ],
  "ddl": [
    "bone-init.sql"
  ]
}
```

### `mime-magic-number` — JAR magic-number 校验

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/service/JarMagicValidator.java"
  ]
}
```

### `artifact-download-auth-url` — 制品鉴权下载 GET :download

```json
{
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/adapter/web/controller/ExtensionManagementController.java",
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/application/command/handler/ExtensionStudioCommandHandler.java"
  ]
}
```

### `rollout-percent-traffic-merge` — rollout_percent ↔ config_json.traffic 收敛（ADR-0014）

```json
{
  "adr": [
    "doc/architecture/adr/0014-extension-rollout-traffic-canonical.md"
  ],
  "java": [
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio/infrastructure/persistence/converter/StudioPersistenceConverter.java"
  ]
}
```
