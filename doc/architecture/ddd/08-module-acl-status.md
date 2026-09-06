# 应用模块 ArchUnit / ACL 状态快照

> **时效声明（2026-08 更新）**：本文为**历史快照**，记录 2026-05 前后各应用模块的 ACL 整改与 ArchUnit 基线状态，**非实时**。当前架构门禁结果以各模块 CI 的 `ArchitectureTest` 为准（规则真源：`bone-framework/bone-architecture-test/BoneDddArchRules`，见 [Bone-DDD 主文档 G-1](../Bone-DDD-最终实践方案.md#g-1-测试与-ciarchunit-规则集)）；建议按 [07 适应度仪表盘](./07-supplements.md) **季度复核**并更新本文。

## 已清零 `application → infrastructure` 存量（2026-05）

| 模块 | 端口（domain/gateway 或 application/port） | 说明 |
|------|------------------------------------------|------|
| bone-iam | `AccessTokenIssuer`、`RefreshTokenIssuer`、`AccountAuthorityCache` | JWT / Redis 缓存 |
| bone-masterdata | `MetaEntityCatalogPort`、`MasterDataExcelImportPort` | catalog 读取、Excel 导入 |
| bone-integration | `CamelFlowExecutionPort`、`IntegrationExecutionRecorder` 等 | 原已用 application/port |

> ⚠️ 上表为 2026-05 状态：6 月重构曾重新引入违规（如 integration `TestConnectorHandler` 直注 `IntegrationExecutionMetrics`），已随整改修复——快照时效性以头部声明为准。

## ArchitectureTest 通过（截至 2026-08）

- 2026-05 已通过：bone-iam、bone-masterdata、bone-blueprint、bone-extension-studio
- 2026-08 整改后通过：bone-integration、bone-system、studio-generator
- bone-metadata-server：SDK 库（豁免 P0-4/5/6/7），架构测试 18 项通过

## 关键整改记录（历史）

- **extension-studio 仓储拆分（ADR-0013，2026-05-22）**：写侧 `domain/repository/*` 仅保留白名单方法；读侧迁入 `domain/gateway/*ReadPort`（`ExtensionReadPort`、`ExtPointReadPort`、`PluginVersionReadPort`、`PluginExecutionLogReadPort`、`StudioAuditReadPort`）。`repository_methods_whitelist` 已去 freeze。
- **bone-iam 聚合根基类（ADR-0011 阶段 1）**：`TenantAggregateRoot` → `Account`、`Role`、`AuditLog`；`AggregateRoot` → `Tenant`、`Permission`。
- **仓储 `*And*` 禁令**：`findBy*And*` 已由 ArchRule 拦截（如原 `findByPluginIdAndVersion` → `findByPluginVersion`）。

## 已知技术债

| 模块 | 项 | 建议 |
|------|-----|------|
| bone-iam | `LocalDateTime` 审计统一 | [ADR-0018](../adr/0018-iam-localdatetime-audit.md) 阶段 2 按需 |

> **规则与 freeze 策略**（不在本文维护副本，避免与主文档漂移）：见主文档 [G-1 测试与 CI](../Bone-DDD-最终实践方案.md#g-1-测试与-ciarchunit-规则集) 与 [附录 B.3 ArchUnit 模板](../Bone-DDD-最终实践方案.md#b3-archunit-模板bone-architecture-test)。
