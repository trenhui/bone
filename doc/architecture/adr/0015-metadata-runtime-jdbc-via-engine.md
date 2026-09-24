# ADR-0015：模式 B 运行时 CRUD 经 engine 直用 JDBC（ADR-MS-001）

| 项 | 内容 |
|----|------|
| **状态** | 已接受（As-Is 例外） |
| **日期** | 2026-05-27 |
| **决策者** | 元数据模块组 |
| **详设** | [元数据管理模块详细设计方案](../../design/modules/2.%20元数据管理模块详细设计方案.md) §2.4 |

---

## 背景

模式 B 需要对**动态表名 / 动态列**执行 CRUD。`bone-metadata-sdk` 的 `Repository` + `FluentQuery` 面向固定实体映射，暂未覆盖「按 `meta_field` 元模型驱动、表名来自 `meta_entity.table_name`」的场景。

## 决策

1. **As-Is**：`bone-metadata-engine-core` 的 `JdbcRuntimeRecordService` 使用 `NamedParameterJdbcTemplate` 拼装 SQL；由 `bone-metadata-server` 的 `RuntimeRecordController` + `CatalogRuntimeEntityProvider` 装配暴露 HTTP。
2. **元模型来源**：catalog 的 `MetaEntity` / `MetaField`（**非** EAV `fields:allocate`）；`CatalogRuntimeEntityProvider` 读取 `status=PUBLISHED` 且 `delivery_mode=RUNTIME` 的实体。
3. **收敛路线（P1）**：在 sdk 抽象 `MetaTableExecutor` SPI（或扩展 `SqlBuilder` 支持元模型表名/列白名单）后，engine 切到 sdk 通道，`JdbcRuntimeRecordService` 退化为 Adapter。

## 理由

- MVP 优先端到端跑通「发布实体 → 物理表存在 → `/api/v1/runtime/**` CRUD」端到端。
- 与 [ADR-0011](./0011-aggregate-root-inheritance.md) 不冲突：问题在**出站持久化通道**，非聚合根继承。

## 后果

### 正面

- 模式 B v1 已可验收（分页上限 200、标识符白名单、软删、租户列可选绑定）。

### 负面 / 风险

- engine 与 sdk「执行统一走 Repository」的架构条文在模式 B 上暂不满足；需在 ArchUnit / 详设中标 **As-Is 例外**，避免误报。
- 若未来拆库（server 与业务库分离），动态 SQL 仍需走 sdk SPI 或只读副本，不能长期复制 JDBC 拼装逻辑。

## 合规

- OpenAPI：[metadata-runtime-v1.yaml](../openapi/metadata-runtime-v1.yaml)
- 收集器：`tools/metadata-compliance-collector` → `runtime-jdbc-service` 扫描项
