# Change: 集成连接器全量与 Camel 编排

**Type**: Feature（P0 业务主价值）
**Status**: Proposed
**Source**: 用户统一优先级规划（阶段2 T4+T5）。基于扫描：integration 后端 50%，20 类连接器仅 3 类真实、HTTP 节点仅执行、编排仅线性。
**Depends on**: 无

## Intent
补齐高频连接器的真实客户端，并推进 Camel 图编排引擎支持分支/并行，使集成引擎真正"连通外部系统"。

## Scope (In)
- `bone-integration` 后端：新增/完善 REST、SOAP、Redis、ES、Mongo、S3 连接器客户端（复用 `ConnectorClientSupport` 接口）；`CamelFlowRuntime` 支持分支/并行节点执行。
- `integration-app` 前端：流程设计器与新增连接器联调验证。

## Scope (Out / Non-Goals)
- 不重构 `ConnectorClientSupport` 抽象层（仅新增实现）。
- 不新增自定义连接器 SDK 协议。

## Assumptions
- `ConnectorClientSupport` 接口已定义，新增连接器仅需实现客户端。
- 前端流程设计器已用 @antv/x6 画图并持久化（flowApi 已通）。

## Acceptance Criteria
- [ ] ≥6 种连接器真实调通（单测/集成验证）。
- [ ] Camel 分支/并行节点可正确执行。
- [ ] 前端设计器与新增连接器联调通过。
