# Proposal: 主数据标准与血缘

## 目标（Why）

`bone-masterdata` 后端 55%、前端 85%，但缺数据标准（DataStandard）与血缘采集（均 0%）。这是主数据成为平台「单一可信源」的标志能力。前端 `qualityResultApi` 是 reject 死代码，后端缺 `quality-results` 接口。

## 范围（What）

1. **数据标准聚合**：新增 `DataStandard` 聚合（`entityCode`/`fieldCode`/`ruleType`（编码规则/参考数据）/`pattern`/`refCode`/`description`）+ Repository + Create/Update/Delete/Query Handler + `DataStandardController`（`/api/v1/mdm/data-standards`）。
2. **血缘采集**：新增 `DataLineageEvent`（source/transform/target/schema）；基于现有领域事件 handler 链路发布；新增 `LineageRecord` 仓储 + `LineageQuery`/Handler 查询上下游。
3. **quality-results 接口**：后端新增 `/api/v1/mdm/quality-results` 查询接口；前端 `masterdata-app` 的 `qualityResultApi` 从 reject 改为真实调用；`QualityResult` 页面接入展示。

## 非目标（Non-Goals）

- 不做主数据全量建模改造、不做数据资产目录/地图大屏。

## 验收标准（Acceptance Criteria）

- [ ] DataStandard CRUD 可用。
- [ ] 血缘采集链路打通（来源→转换→消费可见）。
- [ ] quality-results 接口落地，masterdata-app 接入并展示。
