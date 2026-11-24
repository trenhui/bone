## Context

现有 `ClaimAnalyticsApplicationService` 聚合终态分布/FALLBACK/影子自动化率/P50–P90 时长，自带 scopeNote 防造假；`ClaimAnalyticsController` 提供只读端点，前端 `/analytics` 已是正式菜单。缺的只是按 `tenantId` 分组的维度。详见 proposal.md 的 Why。

## Goals / Non-Goals

**Goals:**
- 在现有指标聚合上叠加租户分组维度，复用同一指标定义
- 保证跨租户隔离，默认无维度回退全量
- 保留 scopeNote 降级语义

**Non-Goals:**
- 不改指标定义、不新增 DDL、不改资金路径

## Decisions

**决策 1：复用现有聚合逻辑，外层按 tenantId 分组。**
不改 `ClaimAnalyticsApplicationService` 的指标计算内核，只在结果层按 tenantId 分组。备选是为每个租户单独跑聚合——否决，因重复计算且易产生口径漂移。

**决策 2：租户隔离在查询层强制。**
分组查询 MUST 按请求方权限过滤 tenantId，不依赖前端传参。备选是前端控制——否决，因存在越权取数风险。

**决策 3：无维度回退全量。**
未指定租户维度时返回与现有一致的全量结果，保证向后兼容。

## Risks / Trade-offs

- [分组查询数据量上升] → 复用现有聚合的时间窗口与只读端点；规模化性能留待容量阶段评估
- [影子指标被误读为出金率] → scopeNote 强制随分组结果返回，测试钉死其存在
- [跨租户泄漏] → 查询层强制过滤 + 隔离测试

## Migration Plan

无数据迁移。部署后默认（无租户维度）行为与现网一致；前端新增租户切换为增量 UI，不影响旧入口。

## Open Questions

无。
