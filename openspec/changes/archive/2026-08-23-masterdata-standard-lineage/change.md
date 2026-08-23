# Change: 主数据标准与血缘

**Type**: Feature（P0 业务标志能力）
**Status**: Proposed
**Source**: 用户统一优先级规划（阶段2 T6）。基于扫描：masterdata 后端 55%、前端 85%，但后端缺数据标准/血缘（0%），前端 qualityApi 未落地（qualityResultApi 为死代码 reject）。
**Depends on**: 无

## Intent
补齐主数据的数据标准（DataStandard）与血缘采集能力，使其成为平台级"单一可信源"标志能力。

## Scope (In)
- `bone-masterdata` 后端：新增 `DataStandard` 聚合（编码规则/参考数据）+ 血缘采集（基于现有领域事件 handler 链路，记录来源/转换/消费）。
- 后端补齐 `quality-results` 接口（masterdata-app qualityResultApi 当前 reject 死代码，需后端落地）。
- `masterdata-app` 前端：接入 `qualityResultApi` 到真实质量结果页面。

## Scope (Out / Non-Goals)
- 不做主数据全量建模改造。
- 不做数据资产目录/数据地图大屏。

## Assumptions
- masterdata 已有领域事件 handler 体系，血缘可挂接。
- 前端 qualityResultApi 已定义但后端无接口，本次补齐。

## Acceptance Criteria
- [ ] DataStandard CRUD 可用。
- [ ] 血缘采集链路打通（来源→转换→消费可见）。
- [ ] quality-results 接口落地，masterdata-app 接入并展示。
