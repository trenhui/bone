---
archived-with: 2026-08-23-masterdata-standard-lineage
status: final
---
# Plan: 主数据标准与血缘

> 对应 `openspec/changes/masterdata-standard-lineage/tasks.md`（16 项任务）。
> 设计：`docs/superpowers/design/masterdata-standard-lineage.md`

## 1. 数据标准聚合
- [x] 新增 `DataStandard` 聚合（`@Table("meta_data_standard")`，entityCode/fieldCode/ruleType/pattern/refCode/description）+ `StandardFieldCode` VO + `StandardRuleType` 枚举
- [x] 新增 `DataStandardRepository` 空接口
- [x] 新增 `DataStandardCommandHandler`（create/update/delete）+ `DataStandardQueryHandler`（page/byEntity）
- [x] 新增 `DataStandardController`（`MASTERDATA_V1 + "/data-standards"`）

## 2. 血缘采集
- [x] 新增 `DataLineageEvent`（source/transform/target/schema，implements DomainEvent）
- [x] 新增 `MasterdataDomainEventPublisher`（Spring ApplicationEventPublisher 路由 domainEvents 到 @TransactionalEventListener handler，打通断裂事件链路）
- [x] 新增 `LineageRecord` 聚合 + `LineageRecordRepository`
- [x] 新增 `DataLineageEventHandler`（@TransactionalEventListener 落库）
- [x] 新增 `LineageQuery` + `LineageQueryHandler` + `LineageRecordCommandHandler` + `LineageController`（POST 记录 + GET 查询）

## 3. quality-results 接口
- [x] 后端新增 `QualityResultController`（`MASTERDATA_V1 + "/quality-results"`）基于 QualityCheck/QualityReport 映射
- [x] 前端 `qualityResultApi.listByRecordId` 从 reject 改为真实调用 `GET /api/v1/masterdata/quality-results?recordId=`
- [x] 新增 `QualityResult.tsx` 页接入 + 路由 + shell 菜单项

## 4. 校验
- [x] `mvn spotless:apply` 通过
- [x] ArchUnit bone-masterdata 18/18 通过
- [x] 前端 tsc 通过（masterdata-app 新页/shell 菜单无错误）
