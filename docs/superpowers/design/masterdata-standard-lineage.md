---
archived-with: 2026-08-23-masterdata-standard-lineage
status: final
---
# Design Doc: 主数据标准与血缘

## 1. 背景与目标

`bone-masterdata`（Java 17，DDD 分层完整）缺数据标准（DataStandard）与血缘采集（均 0%）。这是主数据成为平台「单一可信源」的标志能力。前端 `qualityResultApi` 是 reject 死代码，后端缺 `quality-results` 接口。

目标：补齐 `DataStandard` 聚合（编码规则/参考数据）+ 血缘采集（`DataLineageEvent` + `LineageRecord`）+ `quality-results` 接口 + 前端质量结果页接入。

## 2. 关键决策

### Decision 1：数据标准聚合 DataStandard（对齐 DataQualityRule 范式）
- `DataStandard extends AggregateRoot<Long>`，`@Table("meta_data_standard")`，`@Id @GeneratedValue(DISTRIBUTED_ID)`，`@NoArgsConstructor(access=PRIVATE)`。
- 字段：`entityCode`、`fieldCode`、`ruleType`(枚举：ENCODING/REFERENCE)、`pattern`、`refCode`、`description`、`createdAt`、`updatedAt`。
- 值对象：`StandardFieldCode(String)`（校验非空）、枚举 `StandardRuleType`（getCode()/of(int) 映射 TINYINT）。
- `DataStandardRepository` 空接口（`extends Repository<DataStandard, Long>`，@EnableSqlRepositories 自动实现）。
- Handler：`DataStandardCommandHandler`（create/update/delete）+ `DataStandardQueryHandler`（page/byEntity）。
- Controller：`DataStandardController`（`MASTERDATA_V1 + "/data-standards"`）。

### Decision 2：血缘采集（打通断裂的事件体系）
- **先补事件发布器**：参照 bone-integration 的 `IntegrationDomainEventPublisher`，新建 `MasterdataDomainEventPublisher`（`@Component`），`publishFrom(AggregateRoot)` 取 `getDomainEvents()` + `clearDomainEvents()`，逐条 `dispatch` 到 handler。CommandHandler 在 `@Transactional` 方法末尾调用。
- 新增 `DataLineageEvent implements DomainEvent`（source/transform/target/schema）。
- 新增 `LineageRecord extends AggregateRoot<Long>`（`@Table("meta_data_lineage")`，sourceEntity/sourceField/transformType/targetEntity/targetField/schemaName/createdAt），`LineageRecordRepository` 空接口。
- `DataLineageEventHandler`（`@TransactionalEventListener`）监听 `DataLineageEvent` 落库 `LineageRecord`。
- `LineageQueryHandler`：按 target/source 查询上下游。

### Decision 3：quality-results 接口 + 前端
- 后端新增 `quality-results` 查询接口（`MASTERDATA_V1 + "/quality-results"`），返回质量结果（基于现有 QualityCheck/DataQualityRule 映射，非实时计算）。
- 前端 `masterdata-app` 的 `qualityResultApi.listByRecordId` 从 reject 改为真实调用 `GET /api/v1/masterdata/quality-results?recordId=...`；`QualityResult` 页接入。

## 3. 目录结构（新增）

```
bone-masterdata/src/main/java/com/bone/masterdata/
  domain/standard/DataStandard.java            # 数据标准聚合
  domain/standard/vo/StandardFieldCode.java    # 字段编码值对象
  domain/standard/vo/StandardRuleType.java     # 规则类型枚举
  domain/lineage/LineageRecord.java            # 血缘记录聚合
  domain/lineage/event/DataLineageEvent.java   # 血缘领域事件
  domain/repository/{DataStandardRepository,LineageRecordRepository}.java
  application/event/MasterdataDomainEventPublisher.java   # 事件发布器（打通断裂链路）
  application/event/DataLineageEventHandler.java          # 血缘落库监听
  application/command/cmd/{CreateDataStandardCommand,UpdateDataStandardCommand,DeleteDataStandardCommand}.java
  application/command/handler/DataStandardCommandHandler.java
  application/query/dto/{DataStandardDTO,LineageRecordDTO,QualityResultDTO}.java
  application/query/qry/{DataStandardPageQuery,LineageQuery}.java
  application/query/handler/{DataStandardQueryHandler,LineageQueryHandler}.java
  adapter/web/controller/{DataStandardController,QualityResultController}.java
  adapter/web/converter/{DataStandardWebConverter}.java
  adapter/web/dto/req/{CreateDataStandardReq,UpdateDataStandardReq}.java
  adapter/web/dto/resp/{DataStandardResp}.java
```

## 4. 风险

- masterdata 现有事件体系断裂（有 addDomainEvent + handler，但无 publisher 路由）→ 新建 `MasterdataDomainEventPublisher`，并在现有 CommandHandler 里调用（或仅新链路用）。
- 路径沿用 `MASTERDATA_V1`（`/api/v1/masterdata`），design.md 里的 `/mdm` 是笔误。
- quality-results 非实时计算，返回规则+状态映射。

## 5. 验收对照

- DataStandard CRUD 可用。
- 血缘链路：发布 DataLineageEvent → LineageRecord 落库 → LineageQuery 可见来源→转换→消费。
- quality-results 接口落地，前端 qualityResultApi 真实调用并展示。
