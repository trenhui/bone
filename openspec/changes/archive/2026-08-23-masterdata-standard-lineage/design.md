# Design: 主数据标准与血缘

## Context
`bone-masterdata` 后端 55%，缺数据标准与血缘（0%）。前端 85% 但 `qualityResultApi` 是死代码 reject（无页面调用）、`qualityCheckApi` 后端未落地。需补数据标准聚合、血缘采集、quality-results 接口。

## Decision 1：数据标准聚合
- `DataStandard` 聚合：含 `entityCode`、`fieldCode`、`ruleType`（编码规则/参考数据）、`pattern`、`refCode`（参考数据编码）、`description`。
- 复用 `TenantAbstractEntity` + BaseRepository，CQRS Handler。

## Decision 2：血缘采集
- 基于现有领域事件 handler 链路：在数据写入/转换/消费处发布 `DataLineageEvent`（source/transform/target/schema）。
- 新增 `LineageRecord` 仓储记录血缘边；提供 `LineageQuery` 查询上下游。

## Decision 3：quality-results 接口
- 后端新增 `quality-results` 查询接口（`/api/v1/mdm/quality-results`），返回质量检查结果列表。
- 前端 `masterdata-app` 的 `qualityResultApi` 从 reject 改为调用真实接口；`QualityResult` 页面接入。

## Risks
- 血缘采集需识别现有领域事件发布点（需先摸清 masterdata 事件体系）。
- quality 规则执行引擎若未实现，quality-results 可先返回规则定义 + 状态（非实时计算）。
