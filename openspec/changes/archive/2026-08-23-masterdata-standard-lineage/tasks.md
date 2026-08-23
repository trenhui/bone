# Tasks: 主数据标准与血缘

## 1. 数据标准聚合
- [x] 新增 `DataStandard` 聚合（`@Table("meta_data_standard")`，entityCode/fieldCode/ruleType/pattern/refCode/description）+ `StandardFieldCode` VO + `StandardRuleType` 枚举
- [x] 新增 `DataStandardRepository` 空接口（@EnableSqlRepositories 自动实现）
- [x] 新增 `DataStandardCommandHandler`（create/update/delete）+ `DataStandardQueryHandler`（page/byEntity）
- [x] 新增 `DataStandardController`（`/api/v1/masterdata/data-standards`）

## 2. 血缘采集
- [x] 新增 `DataLineageEvent`（source/transform/target/schema）
- [x] 新增 `MasterdataDomainEventPublisher`（Spring ApplicationEventPublisher 打通断裂事件链路）
- [x] 在 `LineageRecordCommandHandler` 发布血缘事件（来源→转换→消费链路）
- [x] 新增 `LineageRecord` 聚合 + `LineageRecordRepository` + `DataLineageEventHandler`（落库）
- [x] 新增 `LineageQuery`/`LineageQueryHandler` + `LineageController`（POST 记录 + GET 查询上下游）

## 3. quality-results 接口
- [x] 后端新增 `QualityResultController`（`/api/v1/masterdata/quality-results`）基于现有 QualityCheck/QualityReport 映射
- [x] 前端 `qualityResultApi` 从 reject 改为真实调用
- [x] 新增 `QualityResult.tsx` 页接入展示 + 路由 + shell 菜单项

## 4. 校验
- [x] `mvn spotless:apply` + ArchUnit 通过（bone-masterdata 18/18）
- [x] 前端 tsc 通过（新页/菜单无错误）
