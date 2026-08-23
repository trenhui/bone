# Change: 网关统一鉴权与限流熔断

**Type**: Security / Infrastructure
**Status**: Proposed
**Source**: 用户统一优先级规划（阶段1 T1）。基于扫描：gateway 后端 35%，仅路由 + TraceId 透传，下游各自验签，无限流/熔断=安全闸口缺失。
**Depends on**: iam-org-menu-baseline（依赖统一角色/菜单解析）

## Intent
将 API 网关升级为统一安全闸口：校验 JWT、解析租户/角色并注入 Header 转发、提供 Redis 限流与 Resilience4j 熔断。

## Scope (In)
- `bone-gateway`：新增全局 `JwtAuthGlobalFilter`（校验 JWT + 解析 tenant/role 注入 Header）；Redis 令牌桶限流；Resilience4j 熔断配置；保留现有 TraceId 透传。

## Scope (Out / Non-Goals)
- 不重写各服务自身鉴权（仅网关层统一校验 + 转发上下文）。
- 不做路由重写、不做协议转换。

## Assumptions
- 各服务使用 JWT（JJWT 0.12.x）且密钥可经网关共享（环境变量/配置中心）。
- Redis 已在环境可用（限流依赖）。

## Acceptance Criteria
- [ ] 网关校验 JWT，无效/缺失 token 返回 401。
- [ ] 解析 tenant/role 注入下游请求 Header。
- [ ] 限流规则生效（Redis 令牌桶）。
- [ ] 熔断生效（下游故障时快速失败）。
