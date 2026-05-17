# Bone 消息与领域事件规范

> **文档性质**：Topic 命名、消息信封、幂等消费、死信与 Webhook 的**可执行契约**（从总体架构 §8.4 抽离）。  
> **更新**：2026-05-17  
> **关联**：[BONE-总体架构设计方案](./BONE-总体架构设计方案.md) §8.4、[Bone-API-规范.md](./Bone-API-规范.md) §14.5

---

## 1. 适用范围

| 包含 | 不包含 |
|------|--------|
| 领域事件、RocketMQ/Kafka Topic | REST 契约（见 API 规范） |
| Outbox 发件、消费者幂等 | 同步 RPC 内部调用 |
| 对外 Webhook 签名 | Camel 路由实现细节（见集成模块详设） |

---

## 2. Topic 命名

格式（小写、点分、**末尾主版本**）：

```text
{scope}.{domain}.{resource}_{action}.v{major}
```

| 段 | 取值 | 示例 |
|----|------|------|
| `scope` | `domain` / `platform` | `domain` |
| `domain` | `metadata`、`authz`、`integration`、`extension` | `extension` |
| `resource_action` | 蛇形 + 过去式 | `plugin_deployed` |
| `v{major}` | 不兼容 payload 时递增 | `.v1` |

**禁止**：空格、驼峰、无版本后缀、REST 路径直接当 Topic 名。

**分区键**：默认 `tenant_id`；顺序敏感链路同一 key。

---

## 3. 消息信封（Envelope）

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "PluginDeployed",
  "topic": "domain.extension.plugin_deployed.v1",
  "occurredAt": "2026-05-17T10:00:00.000Z",
  "tenantId": "default",
  "traceId": "a1b2c3d4e5f6",
  "schemaVersion": "1.0",
  "payload": { }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `eventId` | 是 | 消费幂等键 |
| `eventType` | 是 | 与注册表一致 |
| `traceId` | 是 | 与 HTTP MDC 一致 |
| `tenantId` | 是 | 多租户隔离 |
| `schemaVersion` | 是 | payload 版本 |
| `payload` | 是 | 领域载荷 |

时间：**ISO-8601 UTC**（与 API 规范 §14.1 一致）。

---

## 4. 事件注册表（真源摘录）

新增事件：**先登记下表 → 再写 Producer**。

| eventType | topic | partition_key |
|-----------|-------|---------------|
| `EntityPublished` | `domain.metadata.entity_published.v1` | `tenant_id` |
| `CodeGenerated` | `domain.metadata.code_generated.v1` | `tenant_id` |
| `PermissionChanged` | `domain.authz.permission_changed.v1` | `tenant_id` |
| `FlowActivated` | `domain.integration.flow_activated.v1` | `tenant_id` |
| `PluginDeployed` | `domain.extension.plugin_deployed.v1` | `tenant_id` |
| `PluginExecutionFailed` | `domain.extension.plugin_execution_failed.v1` | `tenant_id` |

模块详设附录可扩展；**禁止**未登记临时 Topic。

---

## 5. 投递语义与幂等

| 规则 | 说明 |
|------|------|
| 语义 | **至少一次**；消费者必须幂等 |
| 去重 | `eventId` 或业务唯一键落库（`processed_event` 表） |
| 顺序 | 仅保证分区内顺序；跨分区不保证 |
| 重试 | 可重试错误指数退避；不可重试进 DLQ |

---

## 6. 死信（DLQ）

| 方式 | 命名 |
|------|------|
|  per-topic | `{original_topic}.dlq` |
| 平台统一 | `platform.dead_letter.v1` |

DLQ 消息须人工/工具重放；重放须新 `eventId` 或标记 `replayOf`。

---

## 7. Outbox 模式（推荐）

跨聚合/跨服务写操作：

1. 业务事务内写业务表 + `outbox` 表  
2. 独立发件进程轮询 `outbox` → MQ  
3. 发送成功后标记 `outbox` 已发送  

避免「DB 提交 + MQ 发送」双写不一致。

---

## 8. Webhook 出站（HTTP）

| 规则 | 说明 |
|------|------|
| 签名 | `X-Bone-Signature: HMAC-SHA256(body, secret)` |
| 重试 | 指数退避；`eventId` 幂等 |
| 失败 | 死信表（如 `int_dead_letter`） |
| 载荷 | 可复用 Envelope 的 `payload` + 元数据 |

---

## 9. 与可观测性

- Producer/Consumer 记录 `traceId`（见 [Bone-可观测性规范](./Bone-可观测性规范.md)）  
- 指标：`bone_mq_consume_total{topic,status}`、`bone_mq_lag`  

---

## 10. 检查清单

- [ ] Topic 符合命名格式且已登记 §4  
- [ ] 信封含 `eventId`、`tenantId`、`traceId`  
- [ ] 消费者幂等实现  
- [ ] 失败有 DLQ 策略  
- [ ] Webhook 有 HMAC 验签  

---

## 11. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 从总体架构 §8.4 独立为可执行规范 |
