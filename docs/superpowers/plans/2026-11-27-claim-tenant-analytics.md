---
change: claim-tenant-analytics
design-doc: openspec/changes/claim-tenant-analytics/design.md
base-ref: ae05cf9d7
archived-with: 2026-11-27-claim-tenant-analytics
---

<!-- comet-task-authority: openspec/changes/claim-tenant-analytics/tasks.md -->

# Build Plan — claim-tenant-analytics（2026-11-27）

> 真正的功能实现：在既有聚合内核外叠加租户分组维度。完成状态以 tasks.md 为准，本计划仅用 `comet-task-ref` 映射任务 ID。

## 执行配置

- `build_mode=executing-plans` / `tdd_mode=tdd` / `review_mode=thorough` / `subagent_dispatch=null` / `direct_override=false`。

## 基线事实（2026-11-27 实证）

- `ClaimAnalyticsApplicationService.summary(from,to)` 用 `decisionExecutionRepository.findConvergedInWindow` 聚合终态分布/FALLBACK/影子自动化率/平均耗时；另有 case 时长分位/复核率/推送口径（这些仓储方法无 tenant 维度，不可按租户拆）。
- `DecisionExecution extends TenantAggregateRoot<Long>`，`getTenantId(): Long` 可用。
- 当前租户边界：`TenantContext.getTenantId()`（bone-core），未设置返回 null。
- 控制器 `GET /api/v1/claims/analytics/summary` 权限 `claim:claims:read`，只接 from/to。

## 关键设计决策（保守口径，防越权）

1. **不改指标内核、不改仓储/SQL**：分组用现有 `findConvergedInWindow` 查窗口全量后，在 application 层按 tenantId 内存分组（design 决策1）。
2. **权限在服务端强制**：新端点不接收前端 tenantId 参数；只从 `TenantContext.getTenantId()` 取当前租户，仅返回该租户分组（design 决策2）。运营跨租户对比需显式授权（另一个能力），本 change 不越权。
3. **无维度回退全量**：原 `summary()` 保持不变，向后兼容（design 决策3）。
4. **诚实口径**：分组 DTO 只含可从 DecisionExecution 拆分的可复算指标（converged/byDecisionResult/fallbackCount/shadowAutomationRate/avgDurationMs + scopeNote），明确标注 case 时长分位/复核/推送为全量窗口口径、不在分组内返回（不造可拆分的假象）。
5. **scopeNote 强制随分组返回**（防影子值被误读出金率）。

## 全局约束

- 不改指标定义、不新增 DDL、不改资金/赔付语义、不改默认配置。
- 不越权跨租户取数：端点不接收 tenantId 参数，服务端强制当前租户边界。
- 测试数从 mvn 收据取真实值。

## 任务映射与执行顺序

### 阶段 1：现状与口径确认

- 1.1 核对现有指标与 scopeNote 生成逻辑，引用 `ClaimAnalyticsApplicationService.java` L43-125。 <!-- comet-task-ref:01f54f2e-8331-4130-8028-9f6cb9274e88 -->
- 1.2 确认 DecisionExecution 含 tenantId（TenantAggregateRoot）、现有查询未分组。 <!-- comet-task-ref:dbe4f879-eb20-4102-be73-489ade5c38bb -->

### 阶段 2：租户分组实现（TDD）

- 2.1 聚合结果层按 tenantId 分组（内存分组，复用指标内核），各组指标与全量口径一致。 <!-- comet-task-ref:97ba6261-bccc-40d1-97dc-5f950b80d274 -->
- 2.2 查询层强制按请求方权限过滤：新端点只从 TenantContext 取当前租户，不接收前端 tenantId；越权（未设租户上下文）明确拒绝而非返回全量。 <!-- comet-task-ref:bec6fa02-e0a6-453f-a94a-79062433df89 -->
- 2.3 无租户维度时原 `summary()` 回退全量口径，回归测试证明与现有结果一致。 <!-- comet-task-ref:ac9876b9-e9e4-41df-9993-3f9a7fbef891 -->
- 2.4 scopeNote 随每个分组结果返回，表述为影子口径。 <!-- comet-task-ref:cc58e374-3c69-44bb-9b87-bfefe3cf518d -->

### 阶段 3：前端分组视图

- 3.1 `/analytics` 增加租户维度入口（当前租户只读视图，无切换则退化为全量）；诚实展示分组口径边界。 <!-- comet-task-ref:cc2d3ff8-0ca4-4473-9d60-86f5bae876dc -->

### 阶段 4：边界与门禁

- 4.1 确认报表只读、不改指标口径/资金/配置；无写操作。 <!-- comet-task-ref:67df3ee1-9e58-48fb-8828-c6c97b2380c1 -->
- 4.2 变异双向钉子：分组口径/隔离过滤/scopeNote 三处，改必红还原必绿。 <!-- comet-task-ref:6242c046-44f0-4b79-b806-e22f66dc11b3 -->
- 4.3 跑 `ClaimAnalyticsApplicationServiceTest` + 前端 analytics.test 全绿，测试数从收据取。 <!-- comet-task-ref:42f28dc9-91fb-4c59-bfb7-8095ced2af89 -->

## Verification

分组场景各组指标与全量口径一致；越权请求被拒；无维度回退全量；scopeNote 随分组返回；只读无写；变异三处改必红还原必绿；测试全绿。
