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
| `md_standard` | DataStandard | 数据标准 |
| `md_quality_rule` | DataQualityRule | 质量规则 |
| `mdm_qcheck_task` / `mdm_qcheck_report` / `mdm_qcheck_detail` | QualityCheck / QualityReport | 质量检查与报告 |
| `md_lineage` | LineageRecord | 数据血缘 |

> **G6 归口裁定（2026-09-26）**：数据标准 / 质量规则 / 血缘归**主数据上下文**（采集点与业务语义都在主数据转换链路），表名由 `meta_data_*` 改为 `md_*` 对齐前缀语义；元数据模块不再声称拥有。已部署库迁移：`RENAME TABLE meta_data_lineage TO md_lineage, meta_data_standard TO md_standard, meta_data_quality_rule TO md_quality_rule;`

跨上下文只读：`meta_entity`（bone-metadata 所有），见「已知待办」的受控例外说明。

## 租户边界

- `md_entity` / `md_record` / `md_field` / `md_lineage` / `mdm_qcheck_report` / `mdm_qcheck_detail` 已补 `tenant_id`，聚合统一继承 `TenantAggregateRoot` / `TenantAbstractEntity`，读写由 SDK 自动过滤与回填。
- **例外（2026-09-26 实测裁决）**：`DomainTemplate` / `TemplateVersion`（`mdm_domain_template` / `mdm_template_version`）为「平台只写、多方只读」的全局目录，**非租户作用域**（聚合不映射 `tenant_id`，DDL 列保留 DEFAULT 0）。若做成租户作用域，SDK Criteria 通道会把 `tenant_id=0` 平台模板行对租户过滤掉（实例化 404），而 FluentQuery 通道又不过滤，两通道行为分裂。守护测试：`DomainTemplateTenantVisibilityTest`。详见《Bone-多租户规范》§8 约束 4。
- **参考数据 overlay（2026-09-26 裁决，§8 约束 7）**：`ReferenceSet` / `ReferenceValue` 去租户作用域（平台全局目录，仅平台管理员 tenant=0 可写值域/平台值）；租户私有扩展值落 `TenantReferenceValue`（`mdm_reference_value_tenant`，租户作用域）。写路径按 `CurrentUserPort.requireTenantId()` 分发，读路径按值域合并为 `ReferenceValueView`（带 `scope`）。值编码跨两层全局唯一（租户值不得重码平台值）。迁移：`scripts/migration/0003_masterdata_reference_data_overlay.sql`。守护测试：`ReferenceDataOverlayTest`。
- HTTP 入口由 `WebTenantConfiguration` 读取网关注入的 `X-Tenant-Id`；**头缺失时不回落到租户 0**，交由 SDK 失败关闭。
- `@PreAuthorize` 拒绝映射为 **403 `COMMON_FORBIDDEN`**（本模块 `GlobalExceptionHandler` 显式处理 `AccessDeniedException`，不落入 500 兜底）。

## 已知待办

- 数据质量求值器已交付：`RuleExpressionEvaluator` 支持 `NOT_NULL` / `UNIQUE` / `FORMAT` / `RANGE` / `REFERENCE`（表达式语法 `k=v;k=v`，如 `field=code;pattern=^\d{6}$`）；其余类型（如 `CUSTOM` 脚本）**不做猜测式求值**，在检查报告的 `unsupportedRules` 中显式声明未求值，绝不冒充为「通过」。空值除 `NOT_NULL` 外一律跳过。
- `MetaEntityCatalogPortAdapter` 仍用 JDBC 直读他上下文的 `meta_entity`（违背 E-1.1），**拆除条件**：接入元数据服务客户端或联邦视图后改为走 API。
- **SDK 租户缺口已根治（2026-09-25，方案 ②）**：原「`insert()` 取 `TenantContext` 但不回写实体、`DynamicUpdateBuilder` 的 SET 取实体字段 → 新建后立即 `update()` 会把 `tenant_id = NULL` 写进 SET，撞 NOT NULL 报 500，nullable 表则静默破坏跨租户隔离」。**已在 `bone-metadata-sdk` 侧修复**：`DynamicUpdateBuilder` / `BatchUpdateBuilder` / `ConditionalUpdateBuilder` 的 SET 子句统一跳过 `tenant_id`（`UpsertBuilder` 的 `ON DUPLICATE/ON CONFLICT` 同样排除），其值仅在 `INSERT` 时从可信 `TenantContext` 写入一次；WHERE 租户护栏不变。`DynamicUpdateBuilderTest` 契约断言已同步改为「SET 不含 `tenant_id`」。因此该缺陷无需各模块 workaround。本模块 `QualityApplicationService.performCheck` 当前仍用一次 `save()` 落终态（属无害止血），后续可改回正常两步写。
- 鉴权已与 bone-system 对齐：引入 `bone-security` + `JwtAuthenticationFilter`，业务端点 `authenticated`（无状态），仅 OPTIONS / actuator / swagger 放行。
