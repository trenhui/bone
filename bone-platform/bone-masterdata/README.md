# bone-masterdata

主数据服务（企业主数据平台）。

## DDD 约定

- 应用模块，适用 [Bone-DDD 最终实践方案](../../doc/architecture/Bone-DDD-最终实践方案.md)。
- 入站构件：`application/*ApplicationService`（ADR-0028 Application Service First；ADR-0032 收敛后已无 CommandHandler / QueryHandler）。
- 出站端口：`domain/gateway/` — `MetaEntityCatalogPort`（catalog 元实体）、`MasterDataExcelImportPort`（Excel 导入）；实现在 `infrastructure/gateway/`。
- 领域事件：聚合写操作后由 `MasterdataDomainEventPublisher.publishFrom(agg)` 发布，`application/event/*Handler` 以 `@TransactionalEventListener`（AFTER_COMMIT）消费。
- ArchUnit：`src/test/java/com/bone/masterdata/architecture/ArchitectureTest.java`。

## domain 分组形态（E-10 登记）

**目标形态**（[ADR-0036](../../doc/architecture/adr/0036-domain-model-package-single-standard.md)，2026-09-22 起为平台唯一形态）：聚合构件置于 `domain/model/{聚合}/`，聚合根 / 聚合内实体 / 值对象在聚合包内直接平铺，`event/` `projection/` 为聚合内子包；`repository` / `gateway` / `service` 端口留在 `domain/` 根。

**本模块现状**：**已合并为单棵树**（2026-09-22 完成）。原两棵树——`domain/{entity,lineage,quality,record,standard}` 与 `domain/model/{entity,field,quality,record}`——已合并为 `domain/model/{entity,field,lineage,quality,record,standard}/`；`domain/{repository,gateway,service}` 按 ADR-0036 R3 留在 `domain/` 根。合并时已逐包核对双侧同名包（`entity` / `quality` / `record`）的归属。7 个领域测试类随聚合迁至 `domain/model/` 下；59 个测试全绿。

`{聚合}/vo` 已按 ADR-0036 D2 统一为 `domain/model/{聚合}/valueobject/`（如 `quality.valueobject.RuleSeverity`）。本模块命名一致性已无遗留。

## 本上下文拥有的表（E-1.2 数据所有权声明）

| 表 | 归属聚合 | 说明 |
|---|---|---|
| `md_entity` | MasterDataEntity | 主数据实体定义 |
| `md_field` | MasterDataField | 实体字段定义（MasterDataEntity 的子实体） |
| `md_record` | MasterDataRecord | 主数据记录 |
| `meta_data_standard` | DataStandard | 数据标准 |
| `meta_data_quality_rule` | DataQualityRule | 质量规则 |
| `mdm_qcheck_task` / `mdm_qcheck_report` / `mdm_qcheck_detail` | QualityCheck / QualityReport | 质量检查与报告 |
| `meta_data_lineage` | LineageRecord | 数据血缘 |

跨上下文只读：`meta_entity`（bone-metadata 所有），见「已知待办」的受控例外说明。

## 租户边界

- `md_entity` / `md_record` / `md_field` / `meta_data_lineage` / `mdm_qcheck_report` / `mdm_qcheck_detail` 已补 `tenant_id`，聚合统一继承 `TenantAggregateRoot` / `TenantAbstractEntity`，读写由 SDK 自动过滤与回填。
- HTTP 入口由 `WebTenantConfiguration` 读取网关注入的 `X-Tenant-Id`；**头缺失时不回落到租户 0**，交由 SDK 失败关闭。

## 已知待办

- 数据质量「规则表达式求值器」未交付，`PerformDataQualityCheck` 显式返回 501，不产出近似/随机结论。
- `MetaEntityCatalogPortAdapter` 仍用 JDBC 直读他上下文的 `meta_entity`（违背 E-1.1），**拆除条件**：接入元数据服务客户端或联邦视图后改为走 API。
- 鉴权已与 bone-system 对齐：引入 `bone-security` + `JwtAuthenticationFilter`，业务端点 `authenticated`（无状态），仅 OPTIONS / actuator / swagger 放行。
