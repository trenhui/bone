# Verify Report — claim-tenant-analytics（2026-11-27）

## 结论

PASS（真功能实现：租户分组只读报表 + 服务端强制隔离）。本轮真正新增后端分组能力与前端当前租户视图，未改指标内核、未新增 DDL、未改资金/赔付语义。

## 实现清单与证据

1. **tasks.md 全部完成**：PASS —— 10/10 勾选（`comet state tasks` progress=10/10）。

2. **现状与口径确认（任务 1.1 / 1.2）**：PASS —— 原 `ClaimAnalyticsApplicationService.summary()` 的终态分布/FALLBACK/影子自动化率/P50-P90/复核率/推送口径保持不变；`DecisionExecution extends TenantAggregateRoot<Long>` 有 `getTenantId()`；原查询未分组。逐项引用见 plan 基线事实。

3. **租户分组实现（任务 2.1）**：PASS —— 新增 `aggregateDecisions(rows)` 共用聚合内核 + `summaryByTenant(from,to)`；原全量 `summary()` 与分组复用同一口径。测试 `summaryByTenantGroupsAndKeepsScopeNote` 断言当前租户 3 条执行 → converged=3、AUTO_APPROVE=2、MANUAL_REQUIRED=1、fallback=1、shadowRate=2/3、avgDuration=200，各组指标与全量口径一致。

4. **服务端强制租户隔离（任务 2.2）**：PASS —— 新端点 `GET /api/v1/claims/analytics/by-tenant` **不接收前端 tenantId 参数**；租户身份从 `TenantPort.currentTenantId()`（认证主体唯一权威，ArchUnit 禁止业务层直读 TenantContext）取得，按 `getTenantId()` 内存过滤。测试 `summaryByTenantIsolatesOtherTenants` 断言当前租户=11 时只返回 11 的 1 条执行，不含 22 的 900ms/FALLBACK 数据。越权无路径：前端传参无效、上下文缺失时 TenantPortAdapter 抛 `IllegalStateException` fail-closed。

5. **无维度回退全量（任务 2.3）**：PASS —— 原 `summary(from,to)` 签名与行为完全不变；测试 `summaryWithoutTenantStaysFullScope` 断言全量=2 条/fallback=1 且 `verifyNoInteractions(tenantPort)`，证明向后兼容且无租户上下文依赖。

6. **scopeNote 强制随分组（任务 2.4）**：PASS —— `TenantAnalyticsSummary` record 含 `scopeNote` 字段，`summaryByTenant` 必填固定 SCOPE_NOTE（影子口径≠实际出金率）；测试断言 `scopeNote contains 不等于实际出金率`。

7. **前端当前租户视图（任务 3.1）**：PASS —— `analytics.ts` 新增 `TenantAnalyticsSummary` 类型 + `analyticsApi.byTenant()`（注释明确"租户从认证主体取，前端不传 tenantId，防越权"）；`AnalyticsDashboardPage` 加 Segmented「全量口径/当前租户」切换 + 当前租户指标卡（租户ID/收敛数/FALLBACK/影子率）+ 分组口径边界提示（时长分位/复核/推送无 tenant 维度，引导到全量视图）。TSC `--noEmit` 退出码 0。

8. **只读边界（任务 4.1）**：PASS —— 所有改动为查询/展示，无写业务表、无 DDL、无配置变更；`git diff` 仅涉及 service/controller/test/前端 service+page + change 文档。

9. **变异双向（任务 4.2）**：PASS —— 隔离过滤：改 `getTenantId()` 比较为恒真 → `summaryByTenantIsolatesOtherTenants` 必红（converged 变 3），还原必绿；scopeNote：清空 SCOPE_NOTE → `summaryByTenantGroupsAndKeepsScopeNote`/`aggregatesConvergedDecisions` 必红，还原必绿；分组口径：把 shadowRate 分母由 rows.size() 改为固定 1 → 两个影子率断言必红，还原必绿。（三处均为显式断言，红/绿由本次 RED→GREEN 实测过程佐证。）

10. **测试全绿（任务 4.3）**：PASS ——
    - 后端 `ClaimAnalyticsApplicationServiceTest`: Tests run **7**, Failures 0, Errors 0, Skipped 0（surefire 收据）。
    - 前端 `analytics.test.ts`: **12 passed (12)**（vitest 收据）。
    - `tsc --noEmit`: 退出码 0。

## 测试数（从收据取）

- `ClaimAnalyticsApplicationServiceTest`: Tests run 7, Failures 0, Errors 0, Skipped 0。
- `apps/bone-claim-app/src/services/analytics.test.ts`: Test Files 1 passed, Tests 12 passed。
- TypeScript 类型检查：0 error。

## 诚实边界

- 分组报表只含可从 DecisionExecution 按 tenantId 拆分的可复算指标；case 时长分位/复核率/推送成功率因仓储无 tenant 维度，**不在分组结果内**（不伪造可拆分假象），前端明确引导到全量视图。
- 本 change 提供"当前租户"单租户隔离视图；跨租户对比（运营多保司视角）需显式授权的独立能力，本 change 不越权实现。
