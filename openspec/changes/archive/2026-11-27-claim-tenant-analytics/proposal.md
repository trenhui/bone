## Why

运营分析看板已有只读端点 `GET /api/v1/claims/analytics/summary` 与前端 `/analytics` 菜单，聚合终态分布、FALLBACK 降级、影子自动化率、P50–P90 时长，但全部是跨租户全量口径。PRD §14 V2.0 要求「运营报表」支持按保司（租户）维度对比。本 change 在**不改指标口径、不把影子值当出金率、不跨租户泄漏**的前提下，补按 tenant 分组的只读报表能力。

## What Changes

- **租户分组聚合**：`ClaimAnalyticsApplicationService` 增加按 `tenantId` 分组的终态分布、影子自动化率、时长分位聚合。
- **只读端点扩展**：`ClaimAnalyticsController` 增加按租户维度的查询参数与响应分组；沿用现有 `scopeNote` 防「影子值当出金率」造假。
- **前端分组视图**：`/analytics` 增加租户维度切换与对比；无租户维度时退化为现有全量口径。
- **规格与门禁**：新增 capability spec 定义分组口径、租户隔离、降级语义；补变异测试。

**非目标（明确不做）**：不改任何指标定义与计算口径；不把 Shadow 影子自动化率表述为生产出金率；不跨租户返回数据；不新增 DDL；不改资金闸或赔付语义。

## Capabilities

### New Capabilities
- `claim/tenant-analytics`: 按保司（租户）维度的运营报表契约——分组口径、租户隔离、scopeNote 降级语义、无维度时的全量回退，以及变异可证伪性。

### Modified Capabilities
- 无。不修改既有分析能力的需求口径；本 change 增加分组维度，不改指标定义。

## Impact

- **代码**：`bone-apps/synap-claim/synap-claim-app`（application、adapter/web）；前端 `bone-claim-app` 的 `/analytics` 页面。
- **数据**：仅读取现有案件的 `tenantId` 字段，无 schema 变更。
- **测试**：租户隔离、分组正确性、scopeNote 保留、无维度回退场景测试。
- **不受影响**：指标口径、资金路径、provider、DDL、合规分册。
