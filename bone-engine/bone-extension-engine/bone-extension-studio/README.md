# bone-extension-studio

扩展管理控制台（`/api/v1/extension/*`），**应用模块**（DDD 全量 P0）。

## DDD 约定

- 写侧：`domain/repository/*`（§18.2 白名单）
- 读侧：`domain/gateway/*ReadPort`（[ADR-0013](../../../doc/architecture/adr/0013-extension-studio-repository-read-side.md)）
- 实现：`infrastructure/persistence/*` 双接口（`implements XxxRepository, XxxReadPort`）
- ArchUnit：`src/test/java/.../architecture/ArchitectureTest.java`；`repository_methods_whitelist` 直接生效（不 freeze）

## domain 分组形态（E-10 登记）

**目标形态**（[ADR-0036](../../../doc/architecture/adr/0036-domain-model-package-single-standard.md)，2026-09-22 起为平台唯一形态）：聚合构件置于 `domain/model/{聚合}/`，聚合根 / 实体 / 值对象在聚合包内直接平铺，`event/` `projection/` 为聚合内子包；`repository` / `gateway` 端口留在 `domain/` 根。

**本模块现状**：**已迁移至目标形态**（2026-09-22 完成，59 个测试全绿）。原 `domain/model/` 下平铺的 9 个类已按聚合分组：

| 聚合包 | 构件 |
|---|---|
| `domain/model/plugin/` | `PluginVersion`、`DeploymentStatus`、`DeploymentStateMachine` |
| `domain/model/extpoint/` | `ExtPoint` |
| `domain/model/extension/` | `Extension` |
| `domain/model/execution/` | `PluginExecutionLog` |
| `domain/model/marketplace/` | `MarketplaceItem`（record） |
| `domain/model/audit/` | `StudioAuditEntry` |
| `domain/model/operation/` | `StudioOperation`（LRO 状态） |

`domain/{gateway,repository}` 按 ADR-0036 R3 留根；`domain/gateway/*ReadPort` 的存量读端口按 [ADR-0013](../../../doc/architecture/adr/0013-extension-studio-repository-read-side.md) 的迁移路径处理，不随本批搬包。
